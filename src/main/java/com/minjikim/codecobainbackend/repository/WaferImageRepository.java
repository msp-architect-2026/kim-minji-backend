package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.WaferImageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WaferImageRepository extends JpaRepository<WaferImageEntity, Long> {

    Page<WaferImageEntity> findByPredictionContainingIgnoreCaseOrderByCreatedAtDesc(String keyword, Pageable pageable);

    // 일별 통계용
    @Query("SELECT w FROM WaferImageEntity w WHERE w.createdAt >= :from ORDER BY w.createdAt ASC")
    List<WaferImageEntity> findByCreatedAtAfter(@Param("from") LocalDateTime from);

    // 평균 confidence
    @Query("SELECT AVG(w.confidence) FROM WaferImageEntity w")
    Double findAvgConfidence();
}