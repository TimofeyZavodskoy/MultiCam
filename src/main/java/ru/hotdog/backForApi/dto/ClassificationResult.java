package ru.hotdog.backForApi.dto;

import lombok.Data;

@Data
public class ClassificationResult {
    private String quality;
    private double probability;
}