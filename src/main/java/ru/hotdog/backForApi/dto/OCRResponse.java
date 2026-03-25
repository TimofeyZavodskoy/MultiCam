package ru.hotdog.backForApi.dto;

import lombok.Data;

@Data
public class OCRResponse {
    private String tag;
    private String result;
    private String content;
    private String description;
    private String solution;
    private String reasoning;
}
