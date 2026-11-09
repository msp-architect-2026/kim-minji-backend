package com.minjikim.codecobainbackend.entity;

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
@Table(name = "lot_hold")
public class LotHoldEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id")
    private LotEntity lot;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private HoldSource source;

    private String reason;
    private String heldBy;
    private LocalDateTime heldAt;
    private String releasedBy;
    private String releaseComment;
    private LocalDateTime releasedAt;

    public LotHoldEntity(LotEntity lot, HoldSource source, String reason, String heldBy, LocalDateTime heldAt) {
        this.lot = lot;
        this.source = source;
        this.reason = reason;
        this.heldBy = heldBy;
        this.heldAt = heldAt;
    }

    public void release(String releasedBy, String releaseComment, LocalDateTime releasedAt) {
        this.releasedBy = releasedBy;
        this.releaseComment = releaseComment;
        this.releasedAt = releasedAt;
    }
}
