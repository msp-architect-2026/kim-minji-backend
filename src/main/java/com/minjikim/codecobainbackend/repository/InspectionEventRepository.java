package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.InspectionEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InspectionEventRepository extends JpaRepository<InspectionEventEntity, Long> {
    List<InspectionEventEntity> findByInspectionIdOrderById(Long inspectionId);
}
