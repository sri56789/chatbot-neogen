package com.example.pdfchatbot.service;

import com.example.pdfchatbot.model.ChatAnswer;
import com.example.pdfchatbot.model.QueryIntent;
import com.example.pdfchatbot.model.RetrievalResult;
import com.example.pdfchatbot.model.RetrievalResult.RetrievalMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;

@Service
public class SimilaritySearchService {

    private static final Logger logger = LoggerFactory.getLogger(SimilaritySearchService.class);
    
    @Autowired
    private PdfService pdfService;

    @Autowired
    private TextChunkService textChunkService;

    @Autowired
    private LlmService llmService;

    @Autowired
    private PdfVectorClient pdfVectorClient;

    @Autowired
    private QueryIntentClassifier intentClassifier;

    @Value("${rag.retrieval.topK:3}")
    private int topK;

    @Value("${rag.confidence.minChunks:1}")
    private int minChunks;

    @Value("${rag.confidence.minFaissScore:0.2}")
    private double minFaissScore;

    @Value("${catalog.enabled:false}")
    private boolean catalogEnabled;
    
    private List<String> textChunks = new ArrayList<>();
    private volatile boolean indexing = false;
    private volatile String lastIndexError = null;
    private volatile long lastIndexedAt = 0;

    @PostConstruct
    public void initialize() {
        if (catalogEnabled) {
            logger.info("Catalog mode enabled. Skipping PDF indexing on startup.");
            return;
        }
        System.out.println("Initializing PDF index on startup...");
        try {
            reloadDocuments();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize PDF index on startup: " + e.getMessage(), e);
        }
    }
    
    public void reloadDocuments() throws IOException {
        if (catalogEnabled) {
            logger.info("Catalog mode enabled. PDF indexing is disabled.");
            return;
        }
        indexing = true;
        lastIndexError = null;
        System.out.println("Reloading PDF documents...");
        System.out.println("Memory before: " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024 + " MB used");
        try {
            // Process PDFs one at a time to avoid memory issues
            List<String> allChunks = new ArrayList<>();
            List<String> pdfTexts = pdfService.extractTextFromAllPdfs();

            if (pdfTexts.isEmpty()) {
                System.err.println("WARNING: No PDF text extracted. Check if PDFs exist in the pdfs folder.");
                textChunks = allChunks;
                pdfVectorClient.indexChunks(textChunks);
                return;
            }

            System.out.println("Extracted text from " + pdfTexts.size() + " PDF(s)");

            // Process each PDF separately to reduce memory footprint
            for (int i = 0; i < pdfTexts.size(); i++) {
                String pdfText = pdfTexts.get(i);
                System.out.println("Chunking PDF " + (i + 1) + " of " + pdfTexts.size() + " (size: " + pdfText.length() + " chars)");
                List<String> chunks = textChunkService.chunkText(pdfText);
                allChunks.addAll(chunks);
                System.out.println("Created " + chunks.size() + " chunks from PDF " + (i + 1));

                // Suggest GC after processing each PDF
                if (i < pdfTexts.size() - 1) {
                    System.gc();
                }
            }

            textChunks = allChunks;
            System.out.println("Loaded " + textChunks.size() + " text chunks from PDFs");
            System.out.println("Memory after: " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024 + " MB used");

            pdfVectorClient.indexChunks(textChunks);

            lastIndexedAt = System.currentTimeMillis();
        } catch (Exception e) {
            lastIndexError = e.getMessage();
            throw e;
        } finally {
            indexing = false;
        }
    }
    
    public List<String> findMostRelevantChunks(String query, int topK) {
        if (textChunks.isEmpty() || query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        RetrievalResult result = pdfVectorClient.query(query, topK);
        return result.getDocuments();
    }
    
    public int getChunkCount() {
        return textChunks.size();
    }

    public boolean isIndexing() {
        return indexing;
    }

    public String getLastIndexError() {
        return lastIndexError;
    }

    public long getLastIndexedAt() {
        return lastIndexedAt;
    }

    public ChatAnswer answerQuestion(String question,
                                     boolean allowSpeculation,
                                     List<com.example.pdfchatbot.service.ChatHistoryService.ChatEntry> history) {
        QueryIntent intent = intentClassifier.classify(question);
        boolean conversationRef = intentClassifier.isConversationReference(question);
        RetrievalResult retrieval = retrieveChunks(question);
        boolean supported = isSupported(retrieval) || (conversationRef && history != null && !history.isEmpty());
        boolean futuristic = intent != QueryIntent.FACT;
        boolean shouldSpeculate = allowSpeculation && futuristic;

        String answer = generateAnswer(question, retrieval.getDocuments(), history, intent, supported, shouldSpeculate, conversationRef);

        return new ChatAnswer(
                answer,
                intent,
                supported,
                shouldSpeculate,
                retrieval.getMethod(),
                List.of()
        );
    }

    private String generateAnswer(String question,
                                  List<String> relevantChunks,
                                  List<com.example.pdfchatbot.service.ChatHistoryService.ChatEntry> history,
                                  QueryIntent intent,
                                  boolean supported,
                                  boolean allowSpeculation,
                                  boolean conversationRef) {
        boolean hasHistory = history != null && !history.isEmpty();
        boolean hasChunks = relevantChunks != null && !relevantChunks.isEmpty();

        if (!hasChunks && !hasHistory) {
            if (textChunks.isEmpty()) {
                return "I couldn't find relevant information in the PDFs to answer your question. Please make sure you have PDF files in the pdfs folder and restart the server.";
            } else {
                return "I couldn't find relevant information in the PDFs to answer your question. Try rephrasing your question or asking about a different topic.";
            }
        }

        if (!allowSpeculation && (intent != QueryIntent.FACT || !supported) && !conversationRef) {
            logger.info("[llm_skipped] reason=guardrail intent={} supported={}", intent, supported);
            return buildGuardrailAnswer(relevantChunks);
        }

        // Use LLM to generate answer from relevant chunks and recent chat context
        return llmService.generateAnswer(question, relevantChunks, history, intent, supported, allowSpeculation);
    }

    private String buildGuardrailAnswer(List<String> relevantChunks) {
        StringBuilder response = new StringBuilder();
        response.append("This question goes beyond the current knowledge base or is speculative. ");
        response.append("I can't confirm a direct answer from the documents.\n\n");

        if (relevantChunks != null && !relevantChunks.isEmpty()) {
            String excerpt = relevantChunks.get(0);
            excerpt = excerpt.trim().replaceAll("\\s+", " ");
            if (excerpt.length() > 400) {
                excerpt = excerpt.substring(0, 400) + "...";
            }
            response.append("From the documents, the most relevant context is:\n");
            response.append(excerpt);
            response.append("\n\n");
            response.append("If you'd like, you can enable speculative answers for a reasoned guess.");
        } else {
            response.append("If you'd like a speculative answer, enable speculative mode.");
        }

        return response.toString();
    }

    private RetrievalResult retrieveChunks(String question) {
        if (question == null || question.trim().isEmpty()) {
            return new RetrievalResult(List.of(), List.of(), RetrievalMethod.NONE);
        }

        return pdfVectorClient.query(question, topK);
    }

    private boolean isSupported(RetrievalResult result) {
        if (result == null) {
            return false;
        }

        List<String> docs = result.getDocuments();
        if (docs == null || docs.isEmpty() || docs.size() < minChunks) {
            return false;
        }

        if (result.getMethod() == RetrievalMethod.FAISS) {
            List<Double> scores = result.getScores();
            if (scores == null || scores.isEmpty()) {
                return false;
            }
            double maxScore = scores.stream().max(Double::compareTo).orElse(0.0);
            return maxScore >= minFaissScore;
        }

        return false;
    }

}

