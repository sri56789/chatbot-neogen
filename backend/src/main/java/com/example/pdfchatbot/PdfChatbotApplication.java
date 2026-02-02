package com.example.pdfchatbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@SpringBootApplication
public class PdfChatbotApplication {
    private static final Logger logger = LoggerFactory.getLogger(PdfChatbotApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PdfChatbotApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        logger.info("Application is ready to use.");
    }
}