package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.HoldSource;
import com.minjikim.codecobainbackend.entity.LotHoldEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LotHoldRepository extends JpaRepository<LotHoldEntity, Long> {
    boolean existsByLotIdAndSource(Long lotId, HoldSource source);

    Optional<LotHoldEntity> findFirstByLotIdAndReleasedAtIsNull(Long lotId);

    List<LotHoldEntity> findByLotIdOrderByIdDesc(Long lotId);
}
