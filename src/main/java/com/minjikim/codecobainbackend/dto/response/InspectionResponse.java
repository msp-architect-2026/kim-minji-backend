package com.minjikim.codecobainbackend.dto.response;

import com.minjikim.codecobainbackend.entity.InspectionEntity;
import com.minjikim.codecobainbackend.entity.InspectionStatus;

import java.time.LocalDateTime;

public record InspectionResponse(
        Long id,
        Long waferId,
        String filename,
        InspectionStatus status,
        String prediction,
        Double confidence,
        String modelVersion,
        String errorCode,
        String traceId,
        int attempt,
        boolean retryable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static InspectionResponse from(InspectionEntity inspection) {
        return new InspectionResponse(
                inspection.getId(),
                inspection.getWafer() == null ? null : inspection.getWafer().getId(),
                inspection.getFilename(),
                inspection.getStatus(),
                inspection.getPrediction(),
                inspection.getConfidence(),
                inspection.getModelVersion(),
                inspection.getErrorCode(),
                inspection.getTraceId(),
                inspection.getAttempt(),
                inspection.getStatus() == InspectionStatus.FAILED && inspection.getImageKey() != null,
                inspection.getCreatedAt(),
                inspection.getUpdatedAt()
        );
    }
}
