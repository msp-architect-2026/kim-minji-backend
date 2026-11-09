package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.EquipmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentRepository extends JpaRepository<EquipmentEntity, Long> {
    List<EquipmentEntity> findByType(String type);
}
