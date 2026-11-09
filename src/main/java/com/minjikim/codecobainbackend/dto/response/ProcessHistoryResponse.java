package com.minjikim.codecobainbackend.dto.response;

import com.minjikim.codecobainbackend.entity.WaferProcessHistoryEntity;

import java.time.LocalDateTime;

public record ProcessHistoryResponse(
        String stepCode,
        String stepName,
        String equipmentCode,
        String recipe,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
    public static ProcessHistoryResponse from(WaferProcessHistoryEntity history) {
        return new ProcessHistoryResponse(
                history.getStep().getCode(),
                history.getStep().getName(),
                history.getEquipment().getCode(),
                history.getRecipe(),
                history.getStartedAt(),
                history.getEndedAt()
        );
    }
}
