package com.example.pdfchatbot.model;

import java.util.List;

public class RetrievalResult {
    private final List<String> documents;
    private final List<Double> scores;
    private final RetrievalMethod method;

    public RetrievalResult(List<String> documents, List<Double> scores, RetrievalMethod method) {
        this.documents = documents;
        this.scores = scores;
        this.method = method;
    }

    public List<String> getDocuments() {
        return documents;
    }

    public List<Double> getScores() {
        return scores;
    }

    public RetrievalMethod getMethod() {
        return method;
    }

    public enum RetrievalMethod {
        FAISS,
        NONE
    }
}
