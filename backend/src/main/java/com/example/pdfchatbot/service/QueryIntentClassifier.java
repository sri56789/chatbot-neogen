package com.example.pdfchatbot.service;

import com.example.pdfchatbot.model.QueryIntent;
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
import java.util.regex.Pattern;

@Service
public class QueryIntentClassifier {
    private static final Logger logger = LoggerFactory.getLogger(QueryIntentClassifier.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${llm.api.key:}")
    private String apiKey;

    @Value("${llm.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${intent.classifier.enabled:true}")
    private boolean classifierEnabled;

    private static final Pattern SPECULATIVE_PATTERNS = Pattern.compile(
            "\\b(will|would|could|may|might|future|forecast|predict|prediction|next year|next\\s+year|next\\s+month|next\\s+quarter|in\\s+20\\d{2}|by\\s+20\\d{2}|over\\s+the\\s+next|upcoming|roadmap)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FACTUAL_PATTERNS = Pattern.compile(
            "\\b(is|are|was|were|did|does|when|where|who|what|which|how many|how much|current|previous|last year|last\\s+year|historical|budget|amount)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CONVERSATION_PATTERNS = Pattern.compile(
            "\\b(this chat|this conversation|previous question|earlier question|my first question|first question|last question|above|earlier in this chat)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public QueryIntentClassifier() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        this.webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public QueryIntent classify(String question) {
        if (question == null || question.trim().isEmpty()) {
            return QueryIntent.FACT;
        }

        QueryIntent llmIntent = classifyWithLlm(question);
        if (llmIntent != null) {
            return llmIntent;
        }

        boolean futuristic = SPECULATIVE_PATTERNS.matcher(question).find();
        boolean factual = FACTUAL_PATTERNS.matcher(question).find();

        if (futuristic && factual) {
            return QueryIntent.MIXED;
        }
        if (futuristic) {
            return QueryIntent.FUTURISTIC;
        }
        return QueryIntent.FACT;
    }

    public boolean isConversationReference(String question) {
        if (question == null || question.trim().isEmpty()) {
            return false;
        }
        return CONVERSATION_PATTERNS.matcher(question).find();
    }

    private QueryIntent classifyWithLlm(String question) {
        if (!classifierEnabled) {
            return null;
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-3.5-turbo");

            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content",
                    "You are a classifier. Return exactly one label: FACTUAL, FUTURISTIC, or MIXED. " +
                    "FACTUAL: answer exists in existing documents or past data. " +
                    "FUTURISTIC: asks about future events, predictions, outcomes, or unknown states. " +
                    "MIXED: combines factual info with future speculation.");

            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", question);

            requestBody.put("messages", List.of(systemMessage, userMessage));
            requestBody.put("temperature", 0);
            requestBody.put("max_tokens", 5);

            String response = webClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode choices = jsonResponse.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        QueryIntent intent = parseIntent(content.asText());
                        logger.info("[intent_classifier] question={} intent={}", question, intent);
                        return intent;
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    private QueryIntent parseIntent(String content) {
        if (content == null) {
            return null;
        }
        String normalized = content.trim().toUpperCase();
        if (normalized.contains("FACTUAL")) {
            return QueryIntent.FACT;
        }
        if (normalized.contains("FUTURISTIC")) {
            return QueryIntent.FUTURISTIC;
        }
        if (normalized.contains("MIXED")) {
            return QueryIntent.MIXED;
        }
        return null;
    }
}
