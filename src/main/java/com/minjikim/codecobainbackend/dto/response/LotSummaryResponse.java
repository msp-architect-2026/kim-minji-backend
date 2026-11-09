package com.minjikim.codecobainbackend.dto.response;

import com.minjikim.codecobainbackend.entity.LotStatus;

import java.time.LocalDateTime;

public record LotSummaryResponse(
        Long id,
        String lotNo,
        String product,
        LotStatus status,
        long waferCount,
        long passCount,
        long defectCount,
        long reviewCount,
        long failedCount,
        LocalDateTime createdAt
) {
}
