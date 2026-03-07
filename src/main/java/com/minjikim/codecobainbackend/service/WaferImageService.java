package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.AiPredictionResponse;
import com.minjikim.codecobainbackend.entity.WaferImageEntity;
import com.minjikim.codecobainbackend.repository.WaferImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor

public class WaferImageService {
    private final WaferImageRepository waferImageRepository;
    private static final Logger log = LoggerFactory.getLogger(WaferImageService.class);

    public Long save(String filename, String objectKey, AiPredictionResponse response) {
        WaferImageEntity entity = new WaferImageEntity();
        entity.setFilename(filename);
        entity.setFilepath(objectKey);
        entity.setPrediction(response.getPrediction());
        entity.setConfidence(response.getConfidence());
        entity.setCreatedAt(LocalDateTime.now());

        WaferImageEntity saved = waferImageRepository.save(entity);

        log.info("🗃️ 분석 결과 DB 저장 완료: [id={}, filename={}, prediction={}, confidence={}]",
                saved.getId(), saved.getFilename(), saved.getPrediction(), saved.getConfidence());
        return saved.getId();
    }

    public List<WaferImageEntity> findAll() {
        List<WaferImageEntity> list = waferImageRepository.findAll();
        log.info("🔎 DB에서 분석 결과 {}건 조회됨", list.size());
        return list;
    }
}