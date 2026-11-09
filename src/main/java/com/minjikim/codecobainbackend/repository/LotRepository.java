package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.LotEntity;
import com.minjikim.codecobainbackend.entity.LotStatus;
import com.minjikim.codecobainbackend.entity.WaferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface LotRepository extends JpaRepository<LotEntity, Long> {

    boolean existsByLotNo(String lotNo);

    long countByStatus(LotStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LotEntity l WHERE l.id = :id")
    Optional<LotEntity> findByIdForUpdate(@Param("id") Long id);

    Page<LotEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // version을 올리지 않는 단일 UPDATE라, 같은 Lot의 다른 웨이퍼 검사와 낙관적 락 충돌이 나지 않는다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE LotEntity l SET l.status = :status
            WHERE l.id = :lotId AND l.status = :expected AND NOT EXISTS (
                SELECT 1 FROM WaferEntity w WHERE w.lot.id = :lotId AND w.status NOT IN :finished
            )
            """)
    int updateStatusIfAllWafersIn(@Param("lotId") Long lotId,
                                  @Param("expected") LotStatus expected,
                                  @Param("status") LotStatus status,
                                  @Param("finished") Collection<WaferStatus> finished);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE LotEntity l SET l.status = :next WHERE l.id = :lotId AND l.status = :expected")
    int transitionStatus(@Param("lotId") Long lotId, @Param("expected") LotStatus expected, @Param("next") LotStatus next);
}
