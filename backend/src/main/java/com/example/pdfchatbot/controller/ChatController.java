package com.example.pdfchatbot.controller;

import com.example.pdfchatbot.model.ChatAnswer;
import com.example.pdfchatbot.service.CatalogChatService;
import com.example.pdfchatbot.service.CatalogVectorClient;
import com.example.pdfchatbot.service.ChatHistoryService;
import com.example.pdfchatbot.service.SimilaritySearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    @Autowired
    private SimilaritySearchService similaritySearchService;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private CatalogChatService catalogChatService;

    @Autowired
    private CatalogVectorClient catalogVectorClient;

    @Value("${catalog.enabled:false}")
    private boolean catalogEnabled;

    @Value("${catalog.images.dir:../catalog_images}")
    private String catalogImagesDir;
    
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> req) {
        Object rawQuestion = req.get("question");
        String question = rawQuestion instanceof String ? ((String) rawQuestion).trim() : "";
        boolean allowSpeculation = true;
        
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("answer", "Please provide a question."));
        }
        
        try {
            List<ChatHistoryService.ChatEntry> recentHistory = chatHistoryService.getRecentEntries(10);
            ChatAnswer result;
            if (catalogEnabled) {
                result = catalogChatService.answerQuestion(question, recentHistory);
            } else {
                result = similaritySearchService.answerQuestion(question, allowSpeculation, recentHistory);
            }
            String answer = result.getAnswer();

            logQuestionAnswer(question, answer);
            chatHistoryService.addEntry(question, answer);
            logGuardrailDecision(question, result);
            
            Map<String, Object> response = new HashMap<>();
            response.put("answer", answer);
            if (result.getImagePaths() != null && !result.getImagePaths().isEmpty()) {
                response.put("images", buildImageUrls(result.getImagePaths()));
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("answer", "Error processing question: " + e.getMessage()));
        }
    }
    
    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reloadDocuments() {
        try {
            similaritySearchService.reloadDocuments();
            int chunkCount = similaritySearchService.getChunkCount();
            return ResponseEntity.ok(Map.of(
                "status", "Documents reindexed successfully",
                "chunks", String.valueOf(chunkCount)
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "Error reindexing documents: " + e.getMessage()));
        }
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        int chunksLoaded = similaritySearchService.getChunkCount();
        status.put("chunksLoaded", chunksLoaded);
        status.put("indexing", similaritySearchService.isIndexing());
        status.put("lastIndexError", similaritySearchService.getLastIndexError());
        status.put("lastIndexedAt", similaritySearchService.getLastIndexedAt());
        status.put("catalogEnabled", catalogEnabled);

        if (catalogEnabled) {
            Map<String, Object> catalogStatus = catalogVectorClient.status();
            boolean catalogReady = Boolean.TRUE.equals(catalogStatus.get("ready"));
            status.put("ready", catalogReady);
            if (catalogStatus.containsKey("products")) {
                status.put("catalogProducts", catalogStatus.get("products"));
            }
        } else {
            status.put("ready", chunksLoaded > 0);
        }
        return ResponseEntity.ok(status);
    }

    @GetMapping("/catalog/image")
    public ResponseEntity<Resource> catalogImage(@RequestParam("path") String path) {
        try {
            Path baseDir = Paths.get(catalogImagesDir).toAbsolutePath().normalize();
            Path resolved = Paths.get(path);
            if (!resolved.isAbsolute()) {
                resolved = baseDir.resolve(path).normalize();
            } else {
                resolved = resolved.normalize();
            }
            if (!resolved.startsWith(baseDir)) {
                return ResponseEntity.badRequest().build();
            }
            if (!Files.exists(resolved)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(resolved.toUri());
            String contentType = Files.probeContentType(resolved);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }


    private void logQuestionAnswer(String question, String answer) {
        String safeQuestion = truncate(question, 500);
        String safeAnswer = truncate(answer, 1000);
        logger.info("[chat] question={}", safeQuestion);
        logger.info("[chat] answer={}", safeAnswer);
    }

    private void logGuardrailDecision(String question, ChatAnswer result) {
        if (result.getIntent() == com.example.pdfchatbot.model.QueryIntent.FACT && result.isSupported()) {
            return;
        }
        logger.info(
                "[chat_guardrail] intent={} supported={} speculativeAnswer={} retrieval={}",
                result.getIntent(),
                result.isSupported(),
                result.isSpeculativeAnswer(),
                result.getRetrievalMethod()
        );
    }

    private List<String> buildImageUrls(List<String> imagePaths) {
        return imagePaths.stream()
                .distinct()
                .map(p -> "/api/catalog/image?path=" + URLEncoder.encode(p, StandardCharsets.UTF_8))
                .toList();
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen) + "...";
    }
}
