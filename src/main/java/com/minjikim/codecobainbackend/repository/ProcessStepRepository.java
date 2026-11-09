package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.ProcessStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessStepRepository extends JpaRepository<ProcessStepEntity, Long> {
    List<ProcessStepEntity> findAllByOrderBySeq();
}
