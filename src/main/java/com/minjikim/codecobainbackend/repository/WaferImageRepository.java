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
    Page<WaferImageEntity> findByPredictionIgnoreCaseOrderByCreatedAtDesc(String prediction, Pageable pageable);

    @Query("""
            SELECT COUNT(w) AS total,
                   COALESCE(SUM(CASE WHEN LOWER(w.prediction) <> :noneLabel THEN 1 ELSE 0 END), 0) AS defect,
                   AVG(w.confidence) AS avgConfidence
            FROM WaferImageEntity w
            """)
    OverallStats findOverallStats(@Param("noneLabel") String noneLabel);

    @Query(value = """
            SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS statDate,
                   COUNT(*) AS total,
                   COALESCE(SUM(CASE WHEN LOWER(prediction) <> :noneLabel THEN 1 ELSE 0 END), 0) AS defect
            FROM wafer_image
            WHERE created_at >= :from
            GROUP BY statDate
            ORDER BY statDate
            """, nativeQuery = true)
    List<DailyStats> findDailyStats(@Param("from") LocalDateTime from, @Param("noneLabel") String noneLabel);

    @Query("""
            SELECT w.prediction AS prediction, COUNT(w) AS total
            FROM WaferImageEntity w
            WHERE w.prediction IS NOT NULL
            GROUP BY w.prediction
            ORDER BY COUNT(w) DESC
            """)
    List<DefectCount> findDefectCounts();

    @Query("SELECT w FROM WaferImageEntity w WHERE w.createdAt BETWEEN :start AND :end ORDER BY w.createdAt DESC")
    List<WaferImageEntity> findByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    interface OverallStats {
        long getTotal();
        long getDefect();
        Double getAvgConfidence();
    }

    interface DailyStats {
        String getStatDate();
        long getTotal();
        long getDefect();
    }

    interface DefectCount {
        String getPrediction();
        long getTotal();
    }
}
