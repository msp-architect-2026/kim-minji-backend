package com.minjikim.codecobainbackend.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

// AI 서버(predict.py CLASS_NAMES)의 라벨과 일치해야 한다. DB에는 label 문자열을 그대로 저장한다
@Getter
@RequiredArgsConstructor
public enum DefectType {
    CENTER("Center"),
    DONUT("Donut"),
    EDGE_LOC("Edge-Loc"),
    EDGE_RING("Edge-Ring"),
    LOC("Loc"),
    NEAR_FULL("Near-full"),
    RANDOM("Random"),
    SCRATCH("Scratch"),
    NONE("none");

    private final String label;

    public static boolean isKnown(String label) {
        return Arrays.stream(values()).anyMatch(type -> type.label.equalsIgnoreCase(label));
    }
}
