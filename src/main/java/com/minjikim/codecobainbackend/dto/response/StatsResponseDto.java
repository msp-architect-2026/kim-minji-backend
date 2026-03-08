package com.minjikim.codecobainbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StatsResponseDto {
    private long totalCount;
    private long defectCount;
    private double defectRate;
    private double avgConfidence;
}