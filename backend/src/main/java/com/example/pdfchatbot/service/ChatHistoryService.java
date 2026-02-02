package com.example.pdfchatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatHistoryService {
    private final List<ChatEntry> history = Collections.synchronizedList(new ArrayList<>());

    @Value("${chat.history.maxEntries:10}")
    private int maxEntries;

    public void addEntry(String question, String answer) {
        history.add(new ChatEntry(
                Instant.now().toEpochMilli(),
                question,
                answer
        ));
        trimToMaxEntries();
    }

    public List<ChatEntry> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public List<ChatEntry> getRecentEntries(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        synchronized (history) {
            int size = history.size();
            int fromIndex = Math.max(size - limit, 0);
            return new ArrayList<>(history.subList(fromIndex, size));
        }
    }

    public void clear() {
        history.clear();
    }

    private void trimToMaxEntries() {
        if (maxEntries <= 0) {
            history.clear();
            return;
        }
        synchronized (history) {
            while (history.size() > maxEntries) {
                history.remove(0);
            }
        }
    }

    public static class ChatEntry {
        private final long timestamp;
        private final String question;
        private final String answer;

        public ChatEntry(long timestamp, String question, String answer) {
            this.timestamp = timestamp;
            this.question = question;
            this.answer = answer;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getQuestion() {
            return question;
        }

        public String getAnswer() {
            return answer;
        }
    }
}
