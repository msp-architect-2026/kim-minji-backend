package com.minjikim.codecobainbackend.dto.response;

import com.minjikim.codecobainbackend.entity.InspectionEntity;
import com.minjikim.codecobainbackend.entity.InspectionEventEntity;
import com.minjikim.codecobainbackend.entity.InspectionStatus;
import com.minjikim.codecobainbackend.entity.WaferEntity;

import java.time.LocalDateTime;

public record FailureItemResponse(
        Long eventId,
        Long inspectionId,
        Long lotId,
        String lotNo,
        Integer slotNo,
        String stage,
        String errorCode,
        String traceId,
        LocalDateTime occurredAt,
        InspectionStatus currentStatus,
        boolean retryable
) {
    public static FailureItemResponse from(InspectionEventEntity event) {
        InspectionEntity inspection = event.getInspection();
        WaferEntity wafer = inspection.getWafer();
        return new FailureItemResponse(
                event.getId(),
                inspection.getId(),
                wafer == null ? null : wafer.getLot().getId(),
                wafer == null ? null : wafer.getLot().getLotNo(),
                wafer == null ? null : wafer.getSlotNo(),
                stageOf(event.getFromStatus()),
                event.getErrorCode(),
                event.getTraceId(),
                event.getOccurredAt(),
                inspection.getStatus(),
                inspection.getStatus() == InspectionStatus.FAILED && inspection.getImageKey() != null
                        && (wafer == null || wafer.getStatus() == com.minjikim.codecobainbackend.entity.WaferStatus.FAILED)
        );
    }

    public static String stageOf(InspectionStatus fromStatus) {
        return switch (fromStatus) {
            case RECEIVED -> "UPLOAD";
            case UPLOADED, INFERRING -> "INFERENCE";
            default -> "OTHER";
        };
    }
}
