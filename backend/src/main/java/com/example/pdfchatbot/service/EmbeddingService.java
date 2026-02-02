package com.example.pdfchatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class EmbeddingService {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${embedding.api.key:}")
    private String embeddingApiKey;

    @Value("${embedding.api.url:https://api.openai.com/v1/embeddings}")
    private String embeddingApiUrl;

    @Value("${embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    @Value("${embedding.enabled:true}")
    private boolean embeddingEnabled;

    @Value("${llm.api.key:}")
    private String llmApiKey;

    public EmbeddingService() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public List<Double> embedText(String text) {
        List<List<Double>> embeddings = embedTexts(List.of(text));
        if (embeddings.isEmpty()) {
            return List.of();
        }
        return embeddings.get(0);
    }

    public List<List<Double>> embedTexts(List<String> texts) {
        if (!embeddingEnabled) {
            throw new IllegalStateException("Embeddings are disabled. Set embedding.enabled=true to enable embeddings.");
        }

        String apiKeyValue = resolveApiKey();
        if (apiKeyValue.isEmpty()) {
            throw new IllegalStateException("Embedding API key is missing. Set embedding.api.key or LLM_API_KEY.");
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", embeddingModel);
            requestBody.put("input", texts);

            String response = webClient.post()
                    .uri(embeddingApiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKeyValue)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode data = jsonResponse.get("data");
            if (data == null || !data.isArray()) {
                return List.of();
            }

            List<List<Double>> embeddings = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embedding = item.get("embedding");
                if (embedding == null || !embedding.isArray()) {
                    continue;
                }
                List<Double> vector = new ArrayList<>(embedding.size());
                for (JsonNode value : embedding) {
                    vector.add(value.asDouble());
                }
                embeddings.add(vector);
            }

            return embeddings;
        } catch (Exception e) {
            throw new RuntimeException("Error generating embeddings: " + e.getMessage(), e);
        }
    }

    private String resolveApiKey() {
        if (embeddingApiKey != null && !embeddingApiKey.trim().isEmpty()) {
            return embeddingApiKey.trim();
        }
        if (llmApiKey != null && !llmApiKey.trim().isEmpty()) {
            return llmApiKey.trim();
        }
        return "";
    }
}
