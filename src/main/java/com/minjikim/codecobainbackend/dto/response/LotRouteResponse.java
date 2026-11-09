package com.minjikim.codecobainbackend.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record LotRouteResponse(
        Long lotId,
        String currentStep,
        String nextStep,
        List<String> availableEquipment,
        List<Step> steps
) {
    public enum StepStatus {
        PENDING,
        IN_PROGRESS,
        DONE
    }

    public record Step(String stepCode, String stepName, StepStatus status, String equipmentCode, String recipe,
                       LocalDateTime startedAt, LocalDateTime endedAt) {
    }
}
