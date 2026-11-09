package com.minjikim.codecobainbackend.dto.response;

import com.minjikim.codecobainbackend.entity.InspectionEventEntity;
import com.minjikim.codecobainbackend.entity.InspectionStatus;

import java.time.LocalDateTime;

public record InspectionEventResponse(
        Long id,
        InspectionStatus fromStatus,
        InspectionStatus toStatus,
        String errorCode,
        String traceId,
        LocalDateTime occurredAt
) {
    public static InspectionEventResponse from(InspectionEventEntity event) {
        return new InspectionEventResponse(
                event.getId(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getErrorCode(),
                event.getTraceId(),
                event.getOccurredAt()
        );
    }
}
