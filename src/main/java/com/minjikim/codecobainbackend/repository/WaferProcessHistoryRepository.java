package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.WaferProcessHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WaferProcessHistoryRepository extends JpaRepository<WaferProcessHistoryEntity, Long> {

    @Query("""
            SELECT h FROM WaferProcessHistoryEntity h
            JOIN FETCH h.step JOIN FETCH h.equipment
            WHERE h.wafer.id = :waferId
            ORDER BY h.startedAt
            """)
    List<WaferProcessHistoryEntity> findByWaferIdWithDetails(@Param("waferId") Long waferId);

    @Query("""
            SELECT h FROM WaferProcessHistoryEntity h
            JOIN FETCH h.step JOIN FETCH h.equipment
            WHERE h.wafer.lot.id = :lotId
            ORDER BY h.startedAt
            """)
    List<WaferProcessHistoryEntity> findByLotIdWithDetails(@Param("lotId") Long lotId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE WaferProcessHistoryEntity h SET h.endedAt = :endedAt
            WHERE h.step.id = :stepId AND h.endedAt IS NULL
              AND h.wafer.id IN (SELECT w.id FROM WaferEntity w WHERE w.lot.id = :lotId)
            """)
    int closeOpen(@Param("lotId") Long lotId, @Param("stepId") Long stepId, @Param("endedAt") LocalDateTime endedAt);
}
