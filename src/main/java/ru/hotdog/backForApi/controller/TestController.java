package ru.hotdog.backForApi.controller;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hotdog.backForApi.service.IamService;

@RestController
@AllArgsConstructor
@RequestMapping("/api/test")
public class TestController {

    private final IamService iamService;

    @GetMapping("/iam-token")
    public String getIamToken() {
        return iamService.getIamToken();
    }
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

}
