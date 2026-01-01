package ru.hotdog.backForApi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnalysisResult {

    private String type;
    private double confidence;
    private String message;

}
