package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.ProcessStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcessStepRepository extends JpaRepository<ProcessStepEntity, Long> {
    List<ProcessStepEntity> findAllByOrderBySeq();

    Optional<ProcessStepEntity> findByCode(String code);
}
