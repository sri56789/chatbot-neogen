package com.example.pdfchatbot.service;

import com.example.pdfchatbot.model.CatalogProduct;
import com.example.pdfchatbot.model.CatalogSearchResult;
import com.example.pdfchatbot.model.ChatAnswer;
import com.example.pdfchatbot.model.QueryIntent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CatalogChatService {
    @Autowired
    private CatalogVectorClient vectorClient;

    @Autowired
    private QueryIntentClassifier intentClassifier;

    @Autowired
    private LlmService llmService;

    @Value("${catalog.vector.topK:5}")
    private int topK;

    @Value("${catalog.confidence.minScore:0.2}")
    private double minScore;

    public ChatAnswer answerQuestion(String question,
                                     List<com.example.pdfchatbot.service.ChatHistoryService.ChatEntry> history) {
        QueryIntent intent = intentClassifier.classify(question);
        boolean conversationRef = intentClassifier.isConversationReference(question);

        List<CatalogSearchResult> results = vectorClient.query(question, topK);
        boolean supported = hasSupport(results) || (conversationRef && history != null && !history.isEmpty());

        List<String> contextChunks = buildContext(results);
        List<String> imagePaths = collectImagePaths(results);

        String answer = llmService.generateAnswer(
                question,
                contextChunks,
                history,
                intent,
                supported,
                true
        );

        return new ChatAnswer(
                answer,
                intent,
                supported,
                intent != QueryIntent.FACT,
                "FAISS",
                imagePaths
        );
    }

    private boolean hasSupport(List<CatalogSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return false;
        }
        return results.stream().anyMatch(r -> r.getScore() >= minScore);
    }

    private List<String> buildContext(List<CatalogSearchResult> results) {
        List<String> context = new ArrayList<>();
        if (results == null || results.isEmpty()) {
            return context;
        }

        for (CatalogSearchResult result : results) {
            CatalogProduct product = result.getProduct();
            if (product == null) {
                continue;
            }
            StringBuilder chunk = new StringBuilder();
            chunk.append("Product Name: ").append(safe(product.getProductName())).append("\n");
            chunk.append("Model Number: ").append(safe(product.getModelNumber())).append("\n");
            chunk.append("Dimensions: ").append(safe(product.getDimensions())).append("\n");
            chunk.append("Materials: ").append(safe(product.getMaterials())).append("\n");
            chunk.append("Colors: ").append(safe(product.getColors())).append("\n");
            chunk.append("Mount Type: ").append(safe(product.getMountType())).append("\n");
            chunk.append("Pricing: ").append(safe(product.getPricing())).append("\n");
            chunk.append("Notes: ").append(safe(product.getNotes())).append("\n");
            chunk.append("Source: ").append(safe(product.getSourcePdf()))
                    .append(" (page ").append(product.getSourcePage()).append(")\n");
            chunk.append("Image: ").append(safe(product.getImagePath())).append("\n");
            chunk.append("Score: ").append(result.getScore()).append("\n");
            context.add(chunk.toString());
        }

        return context;
    }

    private List<String> collectImagePaths(List<CatalogSearchResult> results) {
        List<String> paths = new ArrayList<>();
        if (results == null || results.isEmpty()) {
            return paths;
        }
        for (CatalogSearchResult result : results) {
            CatalogProduct product = result.getProduct();
            if (product == null) {
                continue;
            }
            String imagePath = product.getImagePath();
            if (imagePath == null || imagePath.isBlank()) {
                continue;
            }
            paths.add(imagePath.trim());
        }
        return paths;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value.trim();
    }
}
