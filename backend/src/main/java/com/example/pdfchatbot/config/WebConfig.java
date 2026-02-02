package com.example.pdfchatbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Forward root and all non-API routes to index.html for SPA routing
        // API routes (/api/*) are handled by @RestController and take precedence
        registry.addViewController("/")
                .setViewName("forward:/index.html");
    }
}
