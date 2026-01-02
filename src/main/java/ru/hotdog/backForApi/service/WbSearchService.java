package ru.hotdog.backForApi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.hotdog.backForApi.dto.WbProduct;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WbSearchService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public List<WbProduct> searchProducts(String query) {
        List<WbProduct> products = new ArrayList<>();

        try {
            String url = UriComponentsBuilder
                    .fromUriString("https://search.wb.ru/exactmatch/ru/common/v4/search")
                    .queryParam("appType", 1)
                    .queryParam("curr", "rub")
                    .queryParam("dest", "-1257786") // Москва
                    .queryParam("resultset", "catalog")
                    .queryParam("query", query)
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("data").get("products");

            for (JsonNode item : items) {
                String id = item.get("id").asText();

                WbProduct product = WbProduct.builder()
                        .article(id)
                        .name(item.path("name").asText())
                        .brand(item.path("brand").asText())
                        .price((double) item.path("salePriceU").asInt() / 100) // Цена в рублях
                        .originalPrice((double)item.path("priceU").asInt() / 100)
                        .rating(item.path("rating").asDouble())
                        .reviews(item.path("feedbacks").asInt())
                        .link("https://www.wildberries.ru/catalog/" + id + "/detail.aspx")
                        .imageUrl(getImageUrl(id))
                        .currency("RUB")
                        .build();

                products.add(product);

                if (products.size() >= 10) break;
            }
        } catch (Exception e) {
            log.error("Ошибка поиска по запросу: {}", query, e);
        }
        return products;
    }

    private String getImageUrl(String id) {
        int _id = Integer.parseInt(id);
        int vol = _id / 100000;
        int part = _id / 1000;
        int basket = 1;

        if (vol >= 0 && vol <= 143) basket = 1;
        else if (vol <= 287) basket = 2;
        else if (vol <= 431) basket = 3;
        else if (vol <= 719) basket = 4;
        else if (vol <= 1007) basket = 5;
        else if (vol <= 1061) basket = 6;
        else if (vol <= 1115) basket = 7;
        else if (vol <= 1169) basket = 8;
        else if (vol <= 1313) basket = 9;
        else if (vol <= 1601) basket = 10;
        else if (vol <= 1655) basket = 11;
        else if (vol <= 1919) basket = 12;
        else if (vol <= 2045) basket = 13;
        else if (vol <= 2189) basket = 14;
        else basket = 15;

        String basketStr = (basket < 10) ? "0" + basket : String.valueOf(basket);
        return String.format("https://basket-%s.wb.ru/vol%d/part%d/%d/images/nm/1.jpg", basketStr, vol, part, _id);
    }
}
