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
    private String modelVersion;
    private String errorCode;
    private String traceId;
    private String idempotencyKey;
    private int attempt;

    @Version
    private Long version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public InspectionEntity(WaferEntity wafer, String filename, String idempotencyKey, LocalDateTime now) {
        this.wafer = wafer;
        this.filename = filename;
        this.idempotencyKey = idempotencyKey;
        this.status = InspectionStatus.RECEIVED;
        this.attempt = 1;
        this.createdAt = now;
        this.updatedAt = now;
        if (wafer != null) {
            wafer.applyInspection(status, null);
        }
    }

    public InspectionEventEntity receivedEvent(String traceId) {
        this.traceId = traceId;
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
        if (status != InspectionStatus.FAILED || imageKey == null) {
            throw new WaferException(ErrorCode.INSPECTION_NOT_RETRYABLE);
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
        return transitionTo(resultStatus, null, traceId, at);
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
            wafer.applyInspection(next, prediction);
        }
        return new InspectionEventEntity(this, previous, next, eventErrorCode, traceId, at);
    }
}
