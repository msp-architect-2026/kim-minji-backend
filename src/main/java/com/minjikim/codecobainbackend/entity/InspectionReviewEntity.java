package com.minjikim.codecobainbackend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// AI 판정과 사람 판정을 함께 남긴다. RECLASSIFIED 건은 이후 재학습 데이터가 된다
@Entity
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "inspection_review")
public class InspectionReviewEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id")
    private InspectionEntity inspection;

    private String aiPrediction;
    private String finalPrediction;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private ReviewDecision decision;

    private String reviewer;
    private String comment;
    private LocalDateTime reviewedAt;

    public InspectionReviewEntity(InspectionEntity inspection, String finalPrediction, String reviewer,
                                  String comment, LocalDateTime reviewedAt) {
        this.inspection = inspection;
        this.aiPrediction = inspection.getPrediction();
        this.finalPrediction = finalPrediction;
        this.decision = finalPrediction.equalsIgnoreCase(aiPrediction) ? ReviewDecision.CONFIRMED : ReviewDecision.RECLASSIFIED;
        this.reviewer = reviewer;
        this.comment = comment;
        this.reviewedAt = reviewedAt;
    }
}
