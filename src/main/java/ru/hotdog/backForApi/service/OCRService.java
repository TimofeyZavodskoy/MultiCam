package ru.hotdog.backForApi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import ru.hotdog.backForApi.dto.OCRResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class OCRService {
    private final String PYTHON_URL = "http://localhost:8000";
    private final RestTemplate restTemplate = new RestTemplate();

    public String processImage(MultipartFile file) throws Exception {
        OCRResponse classification = callPythonEndpoint(file, "/classify");
        String tag = classification.getTag();

        if ("math".equals(tag) || "mixed".equals(tag)) {
            String gemmaMarkdown = callPythonEndpoint(file, "/ocr-full").getContent();
            String task = prepareTask(gemmaMarkdown);

            Map<String, String> solveResult = solve(task);

            StringBuilder sb = new StringBuilder();
            sb.append("### Распознанное условие\n").append(gemmaMarkdown).append("\n\n");

            if (solveResult != null) {
                sb.append("### Решение\n").append(solveResult.get("solution"));

                String reasoning = solveResult.get("reasoning");
                if (reasoning != null && !reasoning.isEmpty()) {
                    sb.append("\n\n---\n#### Ход мыслей\n").append(reasoning);
                }
            } else {
                sb.append("### Ошибка\nНе удалось получить решение.");
            }

            return sb.toString();
        } else if ("text".equals(tag)) {
            return callPythonEndpoint(file, "/ocr").getResult();
        } else
            return callPythonEndpoint(file, "/description").getDescription();
    }

    @Async
    public CompletableFuture<String> processImageAsync(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();

            return CompletableFuture.supplyAsync(() -> {
                try {
                    return processImage(file);
                } catch (Exception e) {
                    log.error("Error processing image", e);
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private OCRResponse callPythonEndpoint(MultipartFile file, String endpoint) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() { return file.getOriginalFilename(); }
        });
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        return restTemplate.postForObject(PYTHON_URL + endpoint, request, OCRResponse.class);
    }

    private String prepareTask(String gemmaOutput) {
        Pattern pattern = Pattern.compile("\\$\\$?(.*?)\\$\\$?");
        Matcher matcher = pattern.matcher(gemmaOutput);

        StringBuilder mathContent = new StringBuilder();
        while (matcher.find()) {
            mathContent.append(matcher.group(1)).append(" ");
        }

        String result = mathContent.toString().trim();

        return result.isEmpty() ? gemmaOutput : result;
    }

    private Map<String, String> solve(String latex) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("latex", latex);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            ParameterizedTypeReference<Map<String, String>> typeRef = new ParameterizedTypeReference<>() {};

            ResponseEntity<Map<String, String>> response = restTemplate.exchange(
                    PYTHON_URL + "/solve",
                    HttpMethod.POST,
                    request,
                    typeRef
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Step-3.5 Error: {}", e.getMessage());
            return null;
        }
    }
}
