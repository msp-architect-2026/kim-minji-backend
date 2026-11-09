package com.minjikim.codecobainbackend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "wafer_process_history")
public class WaferProcessHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wafer_id")
    private WaferEntity wafer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id")
    private ProcessStepEntity step;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id")
    private EquipmentEntity equipment;

    private String recipe;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public WaferProcessHistoryEntity(WaferEntity wafer, ProcessStepEntity step, EquipmentEntity equipment,
                                     String recipe, LocalDateTime startedAt, LocalDateTime endedAt) {
        this.wafer = wafer;
        this.step = step;
        this.equipment = equipment;
        this.recipe = recipe;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }
}
