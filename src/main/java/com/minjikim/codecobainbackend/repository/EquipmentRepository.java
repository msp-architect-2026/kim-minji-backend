package com.minjikim.codecobainbackend.repository;

import com.minjikim.codecobainbackend.entity.EquipmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<EquipmentEntity, Long> {
    List<EquipmentEntity> findByType(String type);

    Optional<EquipmentEntity> findByCode(String code);
}
