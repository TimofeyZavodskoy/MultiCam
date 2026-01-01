package ru.hotdog.backForApi.service;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;

@Service
@AllArgsConstructor
public class IamService {

    private static final String IAM_URL = "https://iam.api.cloud.yandex.net/iam/v1/tokens";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ConvertToTokenService convertService;

    public String getIamToken() throws IOException {
        String jwt = convertService.generateJwt();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("jwt", jwt);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(IAM_URL, request, Map.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("IAM request failed: " + response.getStatusCode().toString());
        }

        return (String) response.getBody().get("iamToken");
    }
}
