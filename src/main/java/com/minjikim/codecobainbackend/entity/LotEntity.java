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
@Table(name = "lot")
public class LotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String lotNo;
    private String product;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private LotStatus status;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    public LotEntity(String lotNo, String product, LocalDateTime createdAt) {
        this.lotNo = lotNo;
        this.product = product;
        this.status = LotStatus.IN_PROGRESS;
        this.createdAt = createdAt;
    }
}
