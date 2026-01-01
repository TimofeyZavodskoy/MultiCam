package ru.hotdog.backForApi.controller;

import lombok.AllArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.hotdog.backForApi.dto.AnalysisResult;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Base64;

@RestController
@RequestMapping("/api/v1")
@AllArgsConstructor
public class AnalyzeController {

//    private final IamService iamService;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResult> analyze(@RequestParam("image") MultipartFile image) throws IOException {

        File tempFile = File.createTempFile("upload-", ".jpg");
        image.transferTo(tempFile);

        byte[] imageBytes = FileUtils.readFileToByteArray(tempFile);
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        String IamToken;

        //СДЕЛАЙ РЕФРЕШ IAM ТОКЕНА(ПОСТ ЗАПРОС С JWT)

        AnalysisResult result = new AnalysisResult();
        result.setType("unknown");
        result.setConfidence(0.0);
        result.setMessage("File recived: " + tempFile.getAbsolutePath());

        tempFile.deleteOnExit();

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

}
