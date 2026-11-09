package com.minjikim.codecobainbackend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "inspection_event")
public class InspectionEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id")
    private InspectionEntity inspection;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private InspectionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private InspectionStatus toStatus;

    private String errorCode;
    private String traceId;
    private LocalDateTime occurredAt;

    InspectionEventEntity(InspectionEntity inspection, InspectionStatus fromStatus, InspectionStatus toStatus,
                          String errorCode, String traceId, LocalDateTime occurredAt) {
        this.inspection = inspection;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.errorCode = errorCode;
        this.traceId = traceId;
        this.occurredAt = occurredAt;
    }
}
