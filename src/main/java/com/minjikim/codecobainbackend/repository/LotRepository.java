package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.LotEntity;
import com.minjikim.codecobainbackend.entity.LotStatus;
import com.minjikim.codecobainbackend.entity.WaferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface LotRepository extends JpaRepository<LotEntity, Long> {

    boolean existsByLotNo(String lotNo);

    Page<LotEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // version을 올리지 않는 단일 UPDATE라, 같은 Lot의 다른 웨이퍼 검사와 낙관적 락 충돌이 나지 않는다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE LotEntity l SET l.status = :status
            WHERE l.id = :lotId AND NOT EXISTS (
                SELECT 1 FROM WaferEntity w WHERE w.lot.id = :lotId AND w.status NOT IN :finished
            )
            """)
    int updateStatusIfAllWafersIn(@Param("lotId") Long lotId,
                                  @Param("status") LotStatus status,
                                  @Param("finished") Collection<WaferStatus> finished);
}
