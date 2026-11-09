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

// 기록 API는 AI 판정이 나온 검사, 통계·SPC는 최종 판정이 확정된 검사만 대상으로 한다. 진행 중·실패·리뷰 대기 건은 결함률을 왜곡한다
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
                   COALESCE(SUM(CASE WHEN LOWER(i.finalPrediction) <> :noneLabel THEN 1 ELSE 0 END), 0) AS defect,
                   AVG(i.confidence) AS avgConfidence
            FROM InspectionEntity i
            WHERE i.finalPrediction IS NOT NULL
            """)
    OverallStats findOverallStats(@Param("noneLabel") String noneLabel);

    @Query(value = """
            SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS statDate,
                   COUNT(*) AS total,
                   COALESCE(SUM(CASE WHEN LOWER(final_prediction) <> :noneLabel THEN 1 ELSE 0 END), 0) AS defect
            FROM inspection
            WHERE created_at >= :from AND final_prediction IS NOT NULL
            GROUP BY statDate
            ORDER BY statDate
            """, nativeQuery = true)
    List<DailyStats> findDailyStats(@Param("from") LocalDateTime from, @Param("noneLabel") String noneLabel);

    @Query("""
            SELECT i.finalPrediction AS prediction, COUNT(i) AS total
            FROM InspectionEntity i
            WHERE i.finalPrediction IS NOT NULL
            GROUP BY i.finalPrediction
            ORDER BY COUNT(i) DESC
            """)
    List<DefectCount> findDefectCounts();

    @Query("SELECT COUNT(i) FROM InspectionEntity i WHERE i.finalPrediction IS NOT NULL")
    long countJudged();

    // 재검사한 웨이퍼가 중복 집계되지 않도록 웨이퍼별 최신 최종 판정만 쓴다. defect·equipment가 빈 문자열이면 전체를 뜻한다
    @Query(value = """
            SELECT DATE_FORMAT(j.created_at, '%Y-%m-%d') AS statDate,
                   COUNT(*) AS total,
                   SUM(CASE WHEN (:defect = '' AND LOWER(j.final_prediction) <> :noneLabel) OR j.final_prediction = :defect THEN 1 ELSE 0 END) AS defect
            FROM (
                SELECT wafer_id, created_at, final_prediction,
                       ROW_NUMBER() OVER (PARTITION BY COALESCE(wafer_id, -id) ORDER BY id DESC) AS rn
                FROM inspection
                WHERE final_prediction IS NOT NULL AND created_at >= :from
            ) j
            WHERE j.rn = 1 AND (:equipment = '' OR EXISTS (
                SELECT 1 FROM wafer_process_history h
                JOIN equipment e ON e.id = h.equipment_id
                WHERE h.wafer_id = j.wafer_id AND e.code = :equipment
            ))
            GROUP BY statDate
            ORDER BY statDate
            """, nativeQuery = true)
    List<DailyStats> findPChartSamples(@Param("from") LocalDateTime from, @Param("noneLabel") String noneLabel,
                                       @Param("defect") String defect, @Param("equipment") String equipment);

    @Query(value = """
            SELECT s.code AS stepCode, e.code AS equipmentCode,
                   COUNT(*) AS total,
                   SUM(CASE WHEN (:defect = '' AND LOWER(j.final_prediction) <> :noneLabel) OR j.final_prediction = :defect THEN 1 ELSE 0 END) AS defect
            FROM (
                SELECT wafer_id, final_prediction,
                       ROW_NUMBER() OVER (PARTITION BY wafer_id ORDER BY id DESC) AS rn
                FROM inspection
                WHERE wafer_id IS NOT NULL AND final_prediction IS NOT NULL AND created_at >= :from
            ) j
            JOIN wafer_process_history h ON h.wafer_id = j.wafer_id
            JOIN process_step s ON s.id = h.step_id
            JOIN equipment e ON e.id = h.equipment_id
            WHERE j.rn = 1 AND NOT EXISTS (
                SELECT 1 FROM wafer_process_history h2
                JOIN equipment e2 ON e2.id = h2.equipment_id
                WHERE h2.wafer_id = j.wafer_id AND e2.code IN (:excluded)
            )
            GROUP BY s.code, s.seq, e.code
            ORDER BY s.seq, e.code
            """, nativeQuery = true)
    List<EquipmentCount> findEquipmentCounts(@Param("from") LocalDateTime from, @Param("noneLabel") String noneLabel,
                                             @Param("defect") String defect, @Param("excluded") Collection<String> excluded);

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

    interface EquipmentCount {
        String getStepCode();
        String getEquipmentCode();
        long getTotal();
        long getDefect();
    }

    interface DefectCount {
        String getPrediction();
        long getTotal();
    }
}
