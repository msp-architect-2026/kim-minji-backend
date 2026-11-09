package com.minjikim.codecobainbackend.dto.response;

import com.minjikim.codecobainbackend.entity.LotStatus;
import com.minjikim.codecobainbackend.entity.WaferStatus;

import java.time.LocalDateTime;
import java.util.List;

public record LotDetailResponse(
        Long id,
        String lotNo,
        String product,
        LotStatus status,
        LocalDateTime createdAt,
        List<WaferSlot> wafers
) {
    public record WaferSlot(Long waferId, int slotNo, WaferStatus status, InspectionResponse latestInspection) {
    }
}
