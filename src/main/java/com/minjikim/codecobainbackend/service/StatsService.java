package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.DailyStatsDto;
import com.minjikim.codecobainbackend.dto.response.DefectDistributionDto;
import com.minjikim.codecobainbackend.dto.response.StatsResponseDto;
import com.minjikim.codecobainbackend.entity.WaferImageEntity;
import com.minjikim.codecobainbackend.repository.WaferImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final WaferImageRepository waferImageRepository;

    public StatsResponseDto getStats() {
        List<WaferImageEntity> all = waferImageRepository.findAll();
        long total = all.size();
        long defect = all.stream()
                .filter(w -> w.getPrediction() != null && !w.getPrediction().equalsIgnoreCase("none"))
                .count();
        double defectRate = total == 0 ? 0 : (double) defect / total * 100;
        Double avgConf = waferImageRepository.findAvgConfidence();
        return new StatsResponseDto(total, defect, defectRate, avgConf == null ? 0 : avgConf);
    }

    public List<DailyStatsDto> getDailyStats(int days) {
        LocalDateTime from = LocalDate.now().minusDays(days - 1).atStartOfDay();
        List<WaferImageEntity> list = waferImageRepository.findByCreatedAtAfter(from);

        Map<LocalDate, List<WaferImageEntity>> grouped = list.stream()
                .collect(Collectors.groupingBy(w -> w.getCreatedAt().toLocalDate()));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    long total = entry.getValue().size();
                    long defect = entry.getValue().stream()
                            .filter(w -> w.getPrediction() != null && !w.getPrediction().equalsIgnoreCase("none"))
                            .count();
                    double rate = total == 0 ? 0 : (double) defect / total * 100;
                    return new DailyStatsDto(entry.getKey().toString(), total, defect, rate);
                })
                .collect(Collectors.toList());
    }

    public List<DefectDistributionDto> getDefectDistribution() {
        List<WaferImageEntity> all = waferImageRepository.findAll();
        long total = all.size();

        Map<String, Long> grouped = all.stream()
                .filter(w -> w.getPrediction() != null)
                .collect(Collectors.groupingBy(WaferImageEntity::getPrediction, Collectors.counting()));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new DefectDistributionDto(
                        entry.getKey(),
                        entry.getValue(),
                        total == 0 ? 0 : (double) entry.getValue() / total * 100
                ))
                .collect(Collectors.toList());
    }
}