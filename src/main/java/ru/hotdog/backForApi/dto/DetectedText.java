package ru.hotdog.backForApi.dto;

import lombok.Data;

@Data
public class DetectedText {
    private String text;
    private String language;
    private double confidence;
}