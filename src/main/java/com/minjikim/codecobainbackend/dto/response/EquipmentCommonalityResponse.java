package com.minjikim.codecobainbackend.dto.response;

public record EquipmentCommonalityResponse(
        String stepCode,
        String equipmentCode,
        long wafers,
        long defects,
        double defectRate,
        long otherWafers,
        double otherDefectRate,
        Double lift,
        double zScore,
        boolean suspect,
        String confoundedWith
) {
}
