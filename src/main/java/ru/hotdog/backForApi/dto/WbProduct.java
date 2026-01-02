package ru.hotdog.backForApi.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WbProduct {
    private String name;
    private String description;
    private String brand;
    private Double price;
    private Double originalPrice;
    private String currency;
    private String link;
    private String imageUrl;
    private Double rating;
    private Integer reviews;
    private String article;
}
