package com.minjikim.codecobainbackend.dto.response;

import com.minjikim.codecobainbackend.entity.InspectionEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AiRecordResponseDto {
    private Long id;
    private String filename;
    private String filepath;
    private String prediction;
    private Double confidence;
    private LocalDateTime createdAt;

    // Entity → DTO 변환 정적 메서드
    public static AiRecordResponseDto from(InspectionEntity entity) {
        return new AiRecordResponseDto(
                entity.getId(),
                entity.getFilename(),
                entity.getImageKey(),
                entity.getPrediction(),
                entity.getConfidence(),
                entity.getCreatedAt()
        );
    }
}
