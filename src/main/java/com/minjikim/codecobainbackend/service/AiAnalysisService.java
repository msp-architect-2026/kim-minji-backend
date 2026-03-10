package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.AiPredictionResponse;
import com.minjikim.codecobainbackend.exception.AiRequestSendException;
import com.minjikim.codecobainbackend.exception.AiServerException;
import com.minjikim.codecobainbackend.exception.InvalidAiResponseException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final RestTemplate restTemplate;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private static final Logger logger = LoggerFactory.getLogger(AiAnalysisService.class);

    @Value("${ai.server.url}${ai.endpoint.predict}")
    private String aiUrl;

    @Value("${minio.bucket}")
    private String bucket;

    @PostConstruct
    public void registerEventListener() {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb =
                circuitBreakerRegistry.circuitBreaker("aiServing");

        cb.getEventPublisher()
                .onStateTransition(event ->
                        logger.warn("Circuit Breaker 상태 변화: {} → {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()));
    }

    public AiPredictionResponse analyze(String objectKey) {
        try {
            return circuitBreakerFactory.create("aiServing").run(
                    () -> callAiServer(objectKey),
                    throwable -> fallback(throwable)
            );
        } catch (Exception e) {
            logger.error("AI 분석 요청 중 오류 발생: {}", e.getMessage(), e);
            throw new AiServerException("AI 분석 실패", e);
        }
    }

    private AiPredictionResponse callAiServer(String objectKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
                "bucket_name", bucket,
                "object_key", objectKey
        );
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        logger.info("AI 서버에 분석 요청 전송: {}", aiUrl);
        logger.info("요청 파라미터: bucket_name={}, object_key={}", bucket, objectKey);

        ResponseEntity<AiPredictionResponse> response = restTemplate.exchange(
                aiUrl,
                HttpMethod.POST,
                request,
                AiPredictionResponse.class
        );

        validateResponse(response);
        logger.info("AI 분석 결과: {}", response.getBody());

        return response.getBody();
    }

    private AiPredictionResponse fallback(Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            logger.warn("Circuit Breaker OPEN 상태 - AI 서버 요청 차단됨");
            throw new AiServerException("AI 서비스 일시 중단 (Circuit Breaker OPEN)", throwable);
        }
        logger.error("AI 서버 호출 실패, fallback 실행: {}", throwable.getMessage());
        throw new AiServerException("AI 분석 실패", throwable);
    }

    private void validateResponse(ResponseEntity<AiPredictionResponse> response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new AiRequestSendException("AI 서버 응답 오류: " + response.getStatusCode());
        }
        AiPredictionResponse body = response.getBody();
        if (body == null) {
            throw new InvalidAiResponseException("AI 응답 body가 null 입니다.");
        }
        if (body.getPrediction() == null || body.getPrediction().isBlank()) {
            throw new InvalidAiResponseException("AI 응답 prediction 값이 비어 있습니다.");
        }
        if (body.getConfidence() == null || body.getConfidence() < 0 || body.getConfidence() > 1) {
            throw new InvalidAiResponseException("AI 응답 confidence 값이 유효하지 않습니다.");
        }
    }
}