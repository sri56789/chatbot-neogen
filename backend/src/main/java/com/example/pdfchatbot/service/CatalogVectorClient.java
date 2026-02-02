package com.example.pdfchatbot.service;

import com.example.pdfchatbot.model.CatalogSearchResult;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogVectorClient {
    private static final Logger logger = LoggerFactory.getLogger(CatalogVectorClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${catalog.vector.url:http://localhost:9000}")
    private String vectorUrl;

    @Value("${catalog.vector.timeoutMs:10000}")
    private long timeoutMs;

    public CatalogVectorClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
        this.webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public List<CatalogSearchResult> query(String question, int topK) {
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
            JsonNode results = json.get("results");
            if (results == null || !results.isArray()) {
                return List.of();
            }

            List<CatalogSearchResult> parsed = new ArrayList<>();
            for (JsonNode item : results) {
                CatalogSearchResult result = objectMapper.convertValue(item, CatalogSearchResult.class);
                if (result != null) {
                    parsed.add(result);
                }
            }

            return parsed;
        } catch (Exception e) {
            logger.warn("Catalog vector query failed: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> status() {
        try {
            String response = webClient.get()
                    .uri(vectorUrl + "/status")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) {
                return Map.of("ready", false);
            }
            JsonNode json = objectMapper.readTree(response);
            Map<String, Object> result = new HashMap<>();
            result.put("ready", json.path("status").asText("").equalsIgnoreCase("ok"));
            if (json.has("products")) {
                result.put("products", json.get("products").asInt());
            }
            return result;
        } catch (Exception e) {
            logger.warn("Catalog vector status check failed: {}", e.getMessage());
            return Map.of("ready", false);
        }
    }
}
