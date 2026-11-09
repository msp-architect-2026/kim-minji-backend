package com.minjikim.codecobainbackend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "wafer")
public class WaferEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id")
    private LotEntity lot;

    private int slotNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private WaferStatus status;

    @Version
    private Long version;

    public WaferEntity(LotEntity lot, int slotNo) {
        this.lot = lot;
        this.slotNo = slotNo;
        this.status = WaferStatus.WAITING;
    }

    void applyInspection(InspectionStatus inspectionStatus, String prediction) {
        status = switch (inspectionStatus) {
            case RECEIVED, UPLOADED, INFERRING -> WaferStatus.INSPECTING;
            case DONE -> DefectType.NONE.getLabel().equalsIgnoreCase(prediction) ? WaferStatus.PASS : WaferStatus.DEFECT;
            case NEED_REVIEW -> WaferStatus.REVIEW;
            case FAILED -> WaferStatus.FAILED;
        };
    }
}
