package com.example.pdfchatbot.model;

public class CatalogSearchResult {
    private CatalogProduct product;
    private double score;

    public CatalogProduct getProduct() {
        return product;
    }

    public void setProduct(CatalogProduct product) {
        this.product = product;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
