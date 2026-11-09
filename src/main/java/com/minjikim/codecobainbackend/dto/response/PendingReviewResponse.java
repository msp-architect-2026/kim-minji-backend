package com.minjikim.codecobainbackend.dto.response;

import com.minjikim.codecobainbackend.entity.InspectionEntity;
import com.minjikim.codecobainbackend.entity.WaferEntity;

import java.time.LocalDateTime;

public record PendingReviewResponse(
        Long inspectionId,
        Long lotId,
        String lotNo,
        Integer slotNo,
        String filename,
        String prediction,
        Double confidence,
        LocalDateTime createdAt
) {
    public static PendingReviewResponse from(InspectionEntity inspection) {
        WaferEntity wafer = inspection.getWafer();
        return new PendingReviewResponse(
                inspection.getId(),
                wafer == null ? null : wafer.getLot().getId(),
                wafer == null ? null : wafer.getLot().getLotNo(),
                wafer == null ? null : wafer.getSlotNo(),
                inspection.getFilename(),
                inspection.getPrediction(),
                inspection.getConfidence(),
                inspection.getCreatedAt()
        );
    }
}
