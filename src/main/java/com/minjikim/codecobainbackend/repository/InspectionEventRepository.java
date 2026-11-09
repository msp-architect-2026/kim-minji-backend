package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.InspectionEventEntity;
import com.minjikim.codecobainbackend.entity.InspectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InspectionEventRepository extends JpaRepository<InspectionEventEntity, Long> {
    List<InspectionEventEntity> findByInspectionIdOrderById(Long inspectionId);

    @Query("""
            SELECT e.fromStatus AS fromStatus, e.errorCode AS errorCode, COUNT(e) AS total,
                   COUNT(DISTINCT CASE WHEN i.status = :failed THEN i.id END) AS unresolved
            FROM InspectionEventEntity e JOIN e.inspection i
            WHERE e.toStatus = :failed AND e.occurredAt >= :from
            GROUP BY e.fromStatus, e.errorCode
            ORDER BY COUNT(e) DESC
            """)
    List<FailureCount> summarizeFailures(@Param("from") LocalDateTime from, @Param("failed") InspectionStatus failed);

    @Query(value = """
            SELECT e FROM InspectionEventEntity e
            JOIN FETCH e.inspection i
            LEFT JOIN FETCH i.wafer w
            LEFT JOIN FETCH w.lot
            WHERE e.toStatus = :failed AND e.occurredAt >= :from AND (:errorCode = '' OR e.errorCode = :errorCode)
            ORDER BY e.id DESC
            """, countQuery = """
            SELECT COUNT(e) FROM InspectionEventEntity e
            WHERE e.toStatus = :failed AND e.occurredAt >= :from AND (:errorCode = '' OR e.errorCode = :errorCode)
            """)
    Page<InspectionEventEntity> findFailures(@Param("from") LocalDateTime from, @Param("failed") InspectionStatus failed,
                                             @Param("errorCode") String errorCode, Pageable pageable);

    interface FailureCount {
        InspectionStatus getFromStatus();
        String getErrorCode();
        long getTotal();
        long getUnresolved();
    }
}
