package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.InspectionEntity;
import com.minjikim.codecobainbackend.entity.InspectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// 기존 기록·통계 API는 판정이 나온 검사(prediction IS NOT NULL)만 대상으로 한다. 진행 중·실패 건은 결함률을 왜곡한다
@Repository
public interface InspectionRepository extends JpaRepository<InspectionEntity, Long> {

    Optional<InspectionEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<InspectionEntity> findByIdAndPredictionIsNotNull(Long id);

    List<InspectionEntity> findByPredictionIsNotNull();

    Page<InspectionEntity> findByPredictionContainingIgnoreCaseOrderByCreatedAtDesc(String keyword, Pageable pageable);
    Page<InspectionEntity> findByPredictionIgnoreCaseOrderByCreatedAtDesc(String prediction, Pageable pageable);

    @Query(value = """
            SELECT i FROM InspectionEntity i
            LEFT JOIN FETCH i.wafer w LEFT JOIN FETCH w.lot
            WHERE i.status = :status
            ORDER BY i.createdAt
            """, countQuery = "SELECT COUNT(i) FROM InspectionEntity i WHERE i.status = :status")
    Page<InspectionEntity> findByStatusWithLot(@Param("status") InspectionStatus status, Pageable pageable);

    @Query("SELECT i FROM InspectionEntity i WHERE i.wafer.id IN :waferIds ORDER BY i.id")
    List<InspectionEntity> findByWaferIdsOrderById(@Param("waferIds") Collection<Long> waferIds);

    @Query("""
            SELECT COUNT(i) AS total,
                   COALESCE(SUM(CASE WHEN LOWER(i.prediction) <> :noneLabel THEN 1 ELSE 0 END), 0) AS defect,
                   AVG(i.confidence) AS avgConfidence
            FROM InspectionEntity i
            WHERE i.prediction IS NOT NULL
            """)
    OverallStats findOverallStats(@Param("noneLabel") String noneLabel);

    @Query(value = """
            SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS statDate,
                   COUNT(*) AS total,
                   COALESCE(SUM(CASE WHEN LOWER(prediction) <> :noneLabel THEN 1 ELSE 0 END), 0) AS defect
            FROM inspection
            WHERE created_at >= :from AND prediction IS NOT NULL
            GROUP BY statDate
            ORDER BY statDate
            """, nativeQuery = true)
    List<DailyStats> findDailyStats(@Param("from") LocalDateTime from, @Param("noneLabel") String noneLabel);

    @Query("""
            SELECT i.prediction AS prediction, COUNT(i) AS total
            FROM InspectionEntity i
            WHERE i.prediction IS NOT NULL
            GROUP BY i.prediction
            ORDER BY COUNT(i) DESC
            """)
    List<DefectCount> findDefectCounts();

    @Query("SELECT COUNT(i) FROM InspectionEntity i WHERE i.prediction IS NOT NULL")
    long countWithPrediction();

    @Query("""
            SELECT i FROM InspectionEntity i
            WHERE i.prediction IS NOT NULL AND i.createdAt BETWEEN :start AND :end
            ORDER BY i.createdAt DESC
            """)
    List<InspectionEntity> findByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

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
