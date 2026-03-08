package com.minjikim.codecobainbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DefectDistributionDto {
    private String prediction;
    private long count;
    private double ratio;
}