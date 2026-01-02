package ru.hotdog.backForApi.dto;

import lombok.Data;

import java.util.List;

@Data
public class AnalysisResult {
    private boolean success;
    private String error;
    private ClassificationResult classification;
    private List<DetectedText> detectedTexts;
    private String fullText;
    private String detectedObject;
    private List<WbProduct> products;
}