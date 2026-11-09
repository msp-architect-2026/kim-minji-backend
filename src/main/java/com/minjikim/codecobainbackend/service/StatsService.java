package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.DailyStatsDto;
import com.minjikim.codecobainbackend.dto.response.DefectDistributionDto;
import com.minjikim.codecobainbackend.dto.response.StatsResponseDto;
import com.minjikim.codecobainbackend.entity.DefectType;
import com.minjikim.codecobainbackend.repository.InspectionRepository;
import com.minjikim.codecobainbackend.repository.InspectionRepository.OverallStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private static final String NONE_LABEL = DefectType.NONE.getLabel();

    private final InspectionRepository inspectionRepository;

    public StatsResponseDto getStats() {
        OverallStats stats = inspectionRepository.findOverallStats(NONE_LABEL);
        Double avgConfidence = stats.getAvgConfidence();
        return new StatsResponseDto(
                stats.getTotal(),
                stats.getDefect(),
                percent(stats.getDefect(), stats.getTotal()),
                avgConfidence == null ? 0 : avgConfidence
        );
    }

    public List<DailyStatsDto> getDailyStats(int days) {
        LocalDateTime from = LocalDate.now().minusDays(days - 1).atStartOfDay();
        return inspectionRepository.findDailyStats(from, NONE_LABEL).stream()
                .map(day -> new DailyStatsDto(
                        day.getStatDate(),
                        day.getTotal(),
                        day.getDefect(),
                        percent(day.getDefect(), day.getTotal())
                ))
                .toList();
    }

    public List<DefectDistributionDto> getDefectDistribution() {
        long total = inspectionRepository.countWithPrediction();
        return inspectionRepository.findDefectCounts().stream()
                .map(defect -> new DefectDistributionDto(
                        defect.getPrediction(),
                        defect.getTotal(),
                        percent(defect.getTotal(), total)
                ))
                .toList();
    }

    private double percent(long part, long total) {
        return total == 0 ? 0 : (double) part / total * 100;
    }
}
