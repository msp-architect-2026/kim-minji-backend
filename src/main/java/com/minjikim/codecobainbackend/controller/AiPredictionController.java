package com.minjikim.codecobainbackend.controller;

import com.minjikim.codecobainbackend.dto.response.AiPredictionResponse;
import com.minjikim.codecobainbackend.service.InspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiPredictionController {

    private final InspectionService inspectionService;

    @PostMapping("/predict")
    public AiPredictionResponse predictImage(@RequestParam("file") MultipartFile file) {
        return inspectionService.inspect(file);
    }
}
