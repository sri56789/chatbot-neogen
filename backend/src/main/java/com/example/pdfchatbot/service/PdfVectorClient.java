package com.example.pdfchatbot.service;

import com.example.pdfchatbot.model.RetrievalResult;
import com.example.pdfchatbot.model.RetrievalResult.RetrievalMethod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PdfVectorClient {
    private static final Logger logger = LoggerFactory.getLogger(PdfVectorClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${pdf.vector.url:http://localhost:9100}")
    private String vectorUrl;

    @Value("${pdf.vector.timeoutMs:60000}")
    private long timeoutMs;

    @Value("${embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    @Value("${pdf.vector.batchSize:128}")
    private int batchSize;

    public PdfVectorClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
        this.webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void indexChunks(List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("chunks", chunks);
            payload.put("model", embeddingModel);
            payload.put("batch_size", batchSize);

            webClient.post()
                    .uri(vectorUrl + "/index")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            throw new RuntimeException("FAISS index request failed: " + e.getMessage(), e);
        }
    }

    public RetrievalResult query(String question, int topK) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("query", question);
            payload.put("top_k", topK);

            String response = webClient.post()
                    .uri(vectorUrl + "/query")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode json = objectMapper.readTree(response);
            JsonNode documentsNode = json.get("documents");
            JsonNode scoresNode = json.get("scores");
            if (documentsNode == null || !documentsNode.isArray()) {
                return new RetrievalResult(List.of(), List.of(), RetrievalMethod.NONE);
            }

            List<String> documents = new java.util.ArrayList<>();
            for (JsonNode node : documentsNode) {
                documents.add(node.asText());
            }
            List<Double> scores = new java.util.ArrayList<>();
            if (scoresNode != null && scoresNode.isArray()) {
                for (JsonNode node : scoresNode) {
                    scores.add(node.asDouble());
                }
            }

            return new RetrievalResult(documents, scores, RetrievalMethod.FAISS);
        } catch (Exception e) {
            logger.warn("FAISS query failed: {}", e.getMessage());
            return new RetrievalResult(List.of(), List.of(), RetrievalMethod.NONE);
        }
    }
}
