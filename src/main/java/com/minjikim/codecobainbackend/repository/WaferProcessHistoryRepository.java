package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.WaferProcessHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WaferProcessHistoryRepository extends JpaRepository<WaferProcessHistoryEntity, Long> {

    @Query("""
            SELECT h FROM WaferProcessHistoryEntity h
            JOIN FETCH h.step JOIN FETCH h.equipment
            WHERE h.wafer.id = :waferId
            ORDER BY h.startedAt
            """)
    List<WaferProcessHistoryEntity> findByWaferIdWithDetails(@Param("waferId") Long waferId);
}
