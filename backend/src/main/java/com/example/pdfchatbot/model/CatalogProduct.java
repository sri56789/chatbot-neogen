package com.example.pdfchatbot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogProduct {
    @JsonProperty("product_name")
    private String productName;
    @JsonProperty("model_number")
    private String modelNumber;
    private String dimensions;
    private String materials;
    private String colors;
    @JsonProperty("mount_type")
    private String mountType;
    private String pricing;
    private String notes;
    @JsonProperty("source_pdf")
    private String sourcePdf;
    @JsonProperty("source_page")
    private int sourcePage;
    @JsonProperty("image_path")
    private String imagePath;

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getModelNumber() {
        return modelNumber;
    }

    public void setModelNumber(String modelNumber) {
        this.modelNumber = modelNumber;
    }

    public String getDimensions() {
        return dimensions;
    }

    @JsonSetter("dimensions")
    public void setDimensions(Object dimensions) {
        this.dimensions = normalizeValue(dimensions);
    }

    public String getMaterials() {
        return materials;
    }

    @JsonSetter("materials")
    public void setMaterials(Object materials) {
        this.materials = normalizeValue(materials);
    }

    public String getColors() {
        return colors;
    }

    @JsonSetter("colors")
    public void setColors(Object colors) {
        this.colors = normalizeValue(colors);
    }

    public String getMountType() {
        return mountType;
    }

    public void setMountType(String mountType) {
        this.mountType = mountType;
    }

    public String getPricing() {
        return pricing;
    }

    public void setPricing(String pricing) {
        this.pricing = pricing;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getSourcePdf() {
        return sourcePdf;
    }

    public void setSourcePdf(String sourcePdf) {
        this.sourcePdf = sourcePdf;
    }

    public int getSourcePage() {
        return sourcePage;
    }

    public void setSourcePage(int sourcePage) {
        this.sourcePage = sourcePage;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    private String normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            return text.isEmpty() ? null : text;
        }
        if (value instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) value;
            return list.stream()
                    .map(item -> item == null ? "" : item.toString().trim())
                    .filter(item -> !item.isEmpty())
                    .distinct()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(null);
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
