package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.AiPredictionResponse;
import com.minjikim.codecobainbackend.entity.DefectType;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final RestTemplate restTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private static final Logger logger = LoggerFactory.getLogger(AiAnalysisService.class);

    @Value("${ai.server.url}${ai.endpoint.predict}")
    private String aiUrl;

    @Value("${minio.bucket}")
    private String bucket;

    @PostConstruct
    public void registerEventListener() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("aiServing");
        cb.getEventPublisher()
                .onStateTransition(event ->
                        logger.warn("Circuit Breaker 상태 변화: {} → {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()));
    }

    public AiPredictionResponse analyze(String objectKey) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("aiServing");
        try {
            return cb.executeSupplier(() -> callAiServer(objectKey));
        } catch (CallNotPermittedException e) {
            throw new WaferException(ErrorCode.AI_CIRCUIT_OPEN, e);
        } catch (ResourceAccessException e) {
            ErrorCode code = e.getCause() instanceof SocketTimeoutException
                    ? ErrorCode.AI_TIMEOUT
                    : ErrorCode.AI_UNAVAILABLE;
            throw new WaferException(code, e);
        } catch (RestClientResponseException e) {
            throw new WaferException(ErrorCode.AI_SERVER_ERROR, "AI 서버 응답 오류: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new WaferException(ErrorCode.AI_INVALID_RESPONSE, e);
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

        logger.info("AI 서버에 분석 요청 전송: bucket_name={}, object_key={}", bucket, objectKey);

        ResponseEntity<AiPredictionResponse> response = restTemplate.exchange(
                aiUrl,
                HttpMethod.POST,
                request,
                AiPredictionResponse.class
        );

        AiPredictionResponse result = response.getBody();
        validateResponse(result);
        logger.info("AI 분석 결과: prediction={}, confidence={}", result.getPrediction(), result.getConfidence());

        return result;
    }

    private void validateResponse(AiPredictionResponse body) {
        if (body == null) {
            throw new WaferException(ErrorCode.AI_INVALID_RESPONSE, "AI 응답 body가 null 입니다.");
        }
        if (!DefectType.isKnown(body.getPrediction())) {
            throw new WaferException(ErrorCode.AI_INVALID_RESPONSE, "AI 응답 prediction 값이 유효하지 않습니다: " + body.getPrediction());
        }
        if (body.getConfidence() == null || body.getConfidence() < 0 || body.getConfidence() > 1) {
            throw new WaferException(ErrorCode.AI_INVALID_RESPONSE, "AI 응답 confidence 값이 유효하지 않습니다.");
        }
    }
}
