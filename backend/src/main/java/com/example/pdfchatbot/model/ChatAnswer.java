package com.example.pdfchatbot.model;

public class ChatAnswer {
    private final String answer;
    private final QueryIntent intent;
    private final boolean supported;
    private final boolean speculativeAnswer;
    private final Object retrievalMethod;
    private final java.util.List<String> imagePaths;

    public ChatAnswer(String answer,
                      QueryIntent intent,
                      boolean supported,
                      boolean speculativeAnswer,
                      Object retrievalMethod,
                      java.util.List<String> imagePaths) {
        this.answer = answer;
        this.intent = intent;
        this.supported = supported;
        this.speculativeAnswer = speculativeAnswer;
        this.retrievalMethod = retrievalMethod;
        this.imagePaths = imagePaths == null ? java.util.List.of() : imagePaths;
    }

    public String getAnswer() {
        return answer;
    }

    public QueryIntent getIntent() {
        return intent;
    }

    public boolean isSupported() {
        return supported;
    }

    public boolean isSpeculativeAnswer() {
        return speculativeAnswer;
    }

    public Object getRetrievalMethod() {
        return retrievalMethod;
    }

    public java.util.List<String> getImagePaths() {
        return imagePaths;
    }
}
