package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.WaferEntity;
import com.minjikim.codecobainbackend.entity.WaferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WaferRepository extends JpaRepository<WaferEntity, Long> {

    List<WaferEntity> findByLotIdOrderBySlotNo(Long lotId);

    @Query("SELECT w FROM WaferEntity w JOIN FETCH w.lot WHERE w.lot.id = :lotId AND w.slotNo = :slotNo")
    Optional<WaferEntity> findByLotIdAndSlotNo(@Param("lotId") Long lotId, @Param("slotNo") int slotNo);

    @Query("""
            SELECT w.lot.id AS lotId, w.status AS status, COUNT(w) AS total
            FROM WaferEntity w
            WHERE w.lot.id IN :lotIds
            GROUP BY w.lot.id, w.status
            """)
    List<LotWaferCount> countByLotIdsAndStatus(@Param("lotIds") Collection<Long> lotIds);

    interface LotWaferCount {
        Long getLotId();
        WaferStatus getStatus();
        long getTotal();
    }
}
