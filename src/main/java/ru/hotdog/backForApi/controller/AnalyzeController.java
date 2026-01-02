package ru.hotdog.backForApi.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.hotdog.backForApi.dto.AnalysisResult;
import ru.hotdog.backForApi.service.IamService;
import ru.hotdog.backForApi.service.VisionService;

import java.io.File;
import java.util.Base64;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalyzeController {

    private final VisionService visionService;
    private final IamService iamService;

    @GetMapping("/iam-token")
    public String getIamToken() {
        return iamService.getIamToken();
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResult> analyze(@RequestParam("image") MultipartFile image) {

        if (image.isEmpty()) {
            AnalysisResult error = new AnalysisResult();
            error.setSuccess(false);
            error.setError("Файл не загружен");
            return ResponseEntity.badRequest().body(error);
        }

        File tempFile = null;

        try {
            tempFile = File.createTempFile("upload-", ".jpg");
            image.transferTo(tempFile);

            byte[] imageBytes = FileUtils.readFileToByteArray(tempFile);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            AnalysisResult result = visionService.analyze(base64Image);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error processing image", e);

            AnalysisResult error = new AnalysisResult();
            error.setSuccess(false);
            error.setError("Ошибка обработки изображения: " + e.getMessage());

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);

        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}