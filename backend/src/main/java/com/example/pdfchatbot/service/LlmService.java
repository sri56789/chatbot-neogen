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

@Service
public class LlmService {
    
    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${llm.api.key:}")
    private String apiKey;
    
    @Value("${llm.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;
    
    @Value("${llm.model:gpt-3.5-turbo}")
    private String model;
    
    @Value("${llm.enabled:true}")
    private boolean enabled;
    
    public LlmService() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    public String generateAnswer(String question,
                                 List<String> contextChunks,
                                 List<com.example.pdfchatbot.service.ChatHistoryService.ChatEntry> history,
                                 QueryIntent intent,
                                 boolean supported,
                                 boolean allowSpeculation) {
        if (!enabled || apiKey == null || apiKey.trim().isEmpty()) {
            // Fallback to simple text extraction if LLM is not configured
            return generateFallbackAnswer(question, contextChunks);
        }
        
        try {
            // Build the prompt with Role → Rules → Data → Task
            StringBuilder context = new StringBuilder();
            context.append("Role:\n");
            context.append("- You are a helpful assistant that answers using PDF context and conversation history.\n\n");

            context.append("Rules:\n");
            context.append("- Never fabricate facts not present in the PDFs or conversation.\n");
            context.append("- If the user asks about the conversation, use the history even if the PDFs don't mention it.\n");
            if (!allowSpeculation) {
                context.append("- If the question is speculative or future-oriented and not supported, say it is not in the knowledge base and provide a cautious inference if possible.\n");
            } else {
                context.append("- If the question is speculative, you may answer but label it as speculative.\n");
            }
            if (!supported) {
                context.append("- Retrieved context does not confidently support a direct answer; be explicit about limits.\n");
            }
            context.append("\n");

            context.append("Data:\n");
            context.append("PDF Context:\n");
            for (int i = 0; i < contextChunks.size() && i < 5; i++) {
                context.append("[Document ").append(i + 1).append("]\n");
                context.append(contextChunks.get(i));
                context.append("\n");
            }

            if (history != null && !history.isEmpty()) {
                context.append("\nConversation History:\n");
                for (com.example.pdfchatbot.service.ChatHistoryService.ChatEntry entry : history) {
                    if (entry.getQuestion() != null && !entry.getQuestion().isBlank()) {
                        context.append("User: ").append(entry.getQuestion().trim()).append("\n");
                    }
                    if (entry.getAnswer() != null && !entry.getAnswer().isBlank()) {
                        context.append("Assistant: ").append(entry.getAnswer().trim()).append("\n");
                    }
                }
            }

            context.append("\nTask:\n");
            context.append("Answer the question below using the data above.\n");
            context.append("Question: ").append(question).append("\n");
            context.append("Answer:");

            logger.info("[llm_prompt] {}", context.toString());
            
            // Build the request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            if (!allowSpeculation) {
                systemMessage.put("content", "Answer only using the provided context. If unsupported or futuristic, say it is not in the knowledge base and provide a cautious inference if possible.");
            } else if (intent == QueryIntent.FUTURISTIC || intent == QueryIntent.MIXED) {
                systemMessage.put("content", "Answer using the provided context. If you speculate, clearly label it as speculative.");
            } else {
                systemMessage.put("content", "Answer using the provided context from PDF documents and conversation history.");
            }
            logger.info("[llm_system] {}", systemMessage.get("content"));
            
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", context.toString());
            
            List<Map<String, String>> messages = List.of(systemMessage, userMessage);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 500);
            
            // Make the API call
            String apiKeyValue = (apiKey != null && !apiKey.isEmpty()) ? apiKey : "";
            String apiUrlValue = (apiUrl != null && !apiUrl.isEmpty()) ? apiUrl : "https://api.openai.com/v1/chat/completions";
            
            if (apiKeyValue.isEmpty()) {
                return generateFallbackAnswer(question, contextChunks);
            }
            
            String response = webClient.post()
                    .uri(apiUrlValue)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKeyValue)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            // Parse the response
            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode choices = jsonResponse.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        return content.asText().trim();
                    }
                }
            }
            
            return "I apologize, but I couldn't generate a proper response. Please try again.";
            
        } catch (Exception e) {
            System.err.println("Error calling LLM API: " + e.getMessage());
            e.printStackTrace();
            // Fallback to simple answer generation
            return generateFallbackAnswer(question, contextChunks);
        }
    }
    
    private String generateFallbackAnswer(String question, List<String> contextChunks) {
        if (contextChunks.isEmpty()) {
            return "I couldn't find relevant information to answer your question.";
        }
        
        // Simple fallback: return the most relevant chunk
        String bestMatch = contextChunks.get(0);
        
        // Try to extract relevant sentences
        String questionLower = question.toLowerCase();
        String[] questionWords = questionLower.split("\\s+");
        
        String[] sentences = bestMatch.split("[.!?]+");
        StringBuilder answer = new StringBuilder();
        
        for (String sentence : sentences) {
            if (sentence == null || sentence.trim().isEmpty()) continue;
            String sentenceLower = sentence.toLowerCase();
            for (String word : questionWords) {
                if (word != null && word.length() > 3 && sentenceLower.contains(word)) {
                    if (answer.length() > 0) answer.append(" ");
                    answer.append(sentence.trim());
                    break;
                }
            }
        }
        
        if (answer.length() > 0) {
            String result = answer.toString();
            return result.length() > 500 ? result.substring(0, 500) + "..." : result;
        }
        
        return bestMatch.length() > 500 ? bestMatch.substring(0, 500) + "..." : bestMatch;
    }
}

