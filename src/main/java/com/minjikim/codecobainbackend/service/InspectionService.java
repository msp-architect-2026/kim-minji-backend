package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.AiPredictionResponse;
import com.minjikim.codecobainbackend.util.ImageFileValidator;
import com.minjikim.codecobainbackend.util.MinioUploader;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class InspectionService {

    private static final Logger log = LoggerFactory.getLogger(InspectionService.class);
    private static final String IMAGE_DIR = "wafer-images";

    private final ImageFileValidator imageFileValidator;
    private final MinioUploader minioUploader;
    private final AiAnalysisService aiAnalysisService;
    private final WaferImageService waferImageService;

    public AiPredictionResponse inspect(MultipartFile file) {
        log.info("검사 요청 수신: 파일명 = {}", file.getOriginalFilename());

        imageFileValidator.validate(file);
        String objectKey = minioUploader.upload(file, IMAGE_DIR);
        AiPredictionResponse response = aiAnalysisService.analyze(objectKey);
        Long savedId = waferImageService.save(file.getOriginalFilename(), objectKey, response);

        return new AiPredictionResponse(savedId, response.getPrediction(), response.getConfidence());
    }
}
