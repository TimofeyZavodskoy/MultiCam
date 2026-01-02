package ru.hotdog.backForApi.service;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IamService {

    private static final String IAM_URL = "https://iam.api.cloud.yandex.net/iam/v1/tokens";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ConvertToTokenService convertService;

    @Cacheable(value = "iamToken")
    public String getIamToken() {
        String jwt = convertService.generateJwt();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("jwt", jwt);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(IAM_URL, HttpMethod.POST, request, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("IAM request failed: " + response.getStatusCode().toString());
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(response.getBody());

        return json.get("iamToken").asText();
    }
}


