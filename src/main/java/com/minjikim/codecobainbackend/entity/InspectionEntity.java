package com.minjikim.codecobainbackend.entity;

import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "inspection")
public class InspectionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Lot 없이 /ai/predict로 올린 건은 null이다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wafer_id")
    private WaferEntity wafer;

    private String filename;
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private InspectionStatus status;

    private String prediction;
    private Double confidence;
    // 자동 확정이면 AI 판정, 리뷰를 거치면 사람 판정이다. 리뷰 전에는 null이다
    private String finalPrediction;
    private String modelVersion;
    private String errorCode;
    private String traceId;
    private String idempotencyKey;
    private int attempt;

    @Version
    private Long version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // traceId를 INSERT 전에 넣어야 저장 직후 UPDATE가 한 번 더 나가지 않는다
    public InspectionEntity(WaferEntity wafer, String filename, String idempotencyKey, String traceId, LocalDateTime now) {
        this.wafer = wafer;
        this.filename = filename;
        this.idempotencyKey = idempotencyKey;
        this.traceId = traceId;
        this.status = InspectionStatus.RECEIVED;
        this.attempt = 1;
        this.createdAt = now;
        this.updatedAt = now;
        if (wafer != null) {
            wafer.applyInspection(status, null);
        }
    }

    public InspectionEventEntity receivedEvent() {
        return new InspectionEventEntity(this, null, status, null, traceId, createdAt);
    }

    public InspectionEventEntity markUploaded(String imageKey, String traceId, LocalDateTime at) {
        this.imageKey = imageKey;
        return transitionTo(InspectionStatus.UPLOADED, null, traceId, at);
    }

    public InspectionEventEntity startInference(String traceId, LocalDateTime at) {
        return transitionTo(InspectionStatus.INFERRING, null, traceId, at);
    }

    public InspectionEventEntity startRetry(String traceId, LocalDateTime at) {
        if (status != InspectionStatus.FAILED || imageKey == null
                || (wafer != null && wafer.getStatus() != WaferStatus.FAILED)) {
            throw new WaferException(ErrorCode.INSPECTION_NOT_RETRYABLE);
        }
        if (wafer != null && wafer.getLot().getStatus() != LotStatus.IN_PROGRESS) {
            throw new WaferException(ErrorCode.LOT_NOT_ACTIVE);
        }
        attempt++;
        errorCode = null;
        return transitionTo(InspectionStatus.INFERRING, null, traceId, at);
    }

    public InspectionEventEntity complete(InspectionStatus resultStatus, String prediction, Double confidence,
                                          String modelVersion, String traceId, LocalDateTime at) {
        this.prediction = prediction;
        this.confidence = confidence;
        this.modelVersion = modelVersion;
        this.finalPrediction = resultStatus == InspectionStatus.DONE ? prediction : null;
        return transitionTo(resultStatus, null, traceId, at);
    }

    public InspectionEventEntity confirmReview(String finalPrediction, String traceId, LocalDateTime at) {
        this.finalPrediction = finalPrediction;
        return transitionTo(InspectionStatus.DONE, null, traceId, at);
    }

    public InspectionEventEntity fail(String errorCode, String traceId, LocalDateTime at) {
        this.errorCode = errorCode;
        return transitionTo(InspectionStatus.FAILED, errorCode, traceId, at);
    }

    private InspectionEventEntity transitionTo(InspectionStatus next, String eventErrorCode, String traceId, LocalDateTime at) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("허용되지 않은 검사 상태 전이: " + status + " → " + next);
        }
        InspectionStatus previous = status;
        status = next;
        this.traceId = traceId;
        updatedAt = at;
        if (wafer != null) {
            wafer.applyInspection(next, finalPrediction);
        }
        return new InspectionEventEntity(this, previous, next, eventErrorCode, traceId, at);
    }
}
