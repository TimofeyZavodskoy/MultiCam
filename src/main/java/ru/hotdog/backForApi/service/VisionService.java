package ru.hotdog.backForApi.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.hotdog.backForApi.dto.AnalysisResult;
import ru.hotdog.backForApi.dto.ClassificationResult;
import ru.hotdog.backForApi.dto.DetectedText;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionService {

    private static final String URL = "https://vision.api.cloud.yandex.net/vision/v1/batchAnalyze";

    private final IamService iamService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final WbSearchService wbSearchService;

    @Value("${yandex.folder-id}")
    private String folderId;

    public AnalysisResult analyze(String base64Image) {
        AnalysisResult result = new AnalysisResult();

        try {
            String jsonResponse = sendRequest(base64Image);
            parseResponse(jsonResponse, result);
            result.setSuccess(true);

            String searchQuery = null;

            if (result.getFullText() != null && !result.getFullText().isBlank()) {
                searchQuery = result.getFullText().split("\n")[0];
            }
            else if (result.getDetectedObject() != null) {
                searchQuery = result.getDetectedObject();
            }

            if (searchQuery != null) {
                log.info("Starting WB search for: {}", searchQuery);
                var products = wbSearchService.searchProducts(searchQuery);
                result.setProducts(products);
            }
        } catch (Exception e) {
            log.error("Error analyzing image", e);
            result.setSuccess(false);
            result.setError("Ошибка анализа: " + e.getMessage());
        }

        return result;
    }

    private String sendRequest(String base64Image) {
        Map<String, Object> body = Map.of(
                "folderId", folderId,
                "analyzeSpecs", List.of(
                        Map.of(
                                "content", base64Image,
                                "features", List.of(
                                        Map.of(
                                                "type", "CLASSIFICATION",
                                                "classificationConfig", Map.of(
                                                        "model", "quality"
                                                )
                                        ),
                                        Map.of(
                                                "type", "TEXT_DETECTION",
                                                "textDetectionConfig", Map.of(
                                                        "languageCodes", List.of("en", "ru")
                                                )
                                        ),
                                        Map.of(
                                                "type", "IMAGE_COPY_SEARCH"
                                        )
                                )
                        )
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(iamService.getIamToken());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        return restTemplate.postForEntity(URL, request, String.class).getBody();
    }

    private void parseResponse(String jsonResponse, AnalysisResult result) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode results = root.path("results").get(0).path("results");

        for (JsonNode resultNode : results) {
            if (resultNode.has("classification")) {
                parseClassification(resultNode.path("classification"), result);
            }

            if (resultNode.has("textDetection")) {
                parseTextDetection(resultNode.path("textDetection"), result);
            }

            if (resultNode.has("objectAnnotation")) {
                parseObjectDetection(resultNode.path("objectAnnotation"), result);
            }

            if (resultNode.has("error")) {
                String errorMsg = resultNode.path("error").path("message").asText();
                log.warn("API returned error: {}", errorMsg);
            }
        }
    }

    private void parseClassification(JsonNode classification, AnalysisResult result) {
        JsonNode properties = classification.path("properties");

        String bestQuality = "";
        double maxProbability = 0.0;

        for (JsonNode prop : properties) {
            String name = prop.path("name").asText();
            double probability = prop.path("probability").asDouble();

            if (probability > maxProbability) {
                maxProbability = probability;
                bestQuality = name;
            }
        }

        ClassificationResult classResult = new ClassificationResult();
        classResult.setQuality(bestQuality);
        classResult.setProbability(maxProbability);
        result.setClassification(classResult);
    }

    private void parseTextDetection(JsonNode textDetection, AnalysisResult result) {
        List<DetectedText> detectedTexts = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        JsonNode pages = textDetection.path("pages");
        for (JsonNode page : pages) {
            JsonNode blocks = page.path("blocks");

            for (JsonNode block : blocks) {
                JsonNode lines = block.path("lines");

                for (JsonNode line : lines) {
                    JsonNode words = line.path("words");
                    List<String> lineWords = new ArrayList<>();

                    for (JsonNode word : words) {
                        String text = word.path("text").asText();
                        double confidence = word.path("confidence").asDouble();
                        String language = word.path("languages").get(0)
                                .path("languageCode").asText();

                        DetectedText detected = new DetectedText();
                        detected.setText(text);
                        detected.setLanguage(language);
                        detected.setConfidence(confidence);
                        detectedTexts.add(detected);

                        lineWords.add(text);
                    }

                    if (!lineWords.isEmpty()) {
                        fullText.append(String.join(" ", lineWords)).append("\n");
                    }
                }
            }
        }

        result.setDetectedTexts(detectedTexts);
        result.setFullText(fullText.toString().trim());
    }

    private void parseObjectDetection(JsonNode objectAnnottation, AnalysisResult result) {
        JsonNode objects = objectAnnottation.path("objects");

        String bestObject = null;
        double maxProbability = 0.0;

        for (JsonNode obj : objects) {
            String name = obj.path("name").asText();
            double probability = obj.path("probability").asDouble();

            if (probability > maxProbability) {
                maxProbability = probability;
                bestObject = name;
            }
        }

        if  (bestObject != null) {
            log.info("Best object detected: {} with probability {}", bestObject,  maxProbability);
            result.setDetectedObject(translateObject(bestObject));
        }
    }

    private String translateObject(String engName) {
        Map<String, String> dictionary = Map.of(
                "Sneakers", "Кроссовки",
                "T-shirt", "Футболка",
                "Outerwear", "Верхняя одежда",
                "Bag", "Сумка",
                "Watch", "Часы"
        );
        return dictionary.getOrDefault(engName, engName);
    }
}