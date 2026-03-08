package com.minjikim.codecobainbackend.controller;

import com.minjikim.codecobainbackend.dto.response.DailyStatsDto;
import com.minjikim.codecobainbackend.dto.response.DefectDistributionDto;
import com.minjikim.codecobainbackend.dto.response.StatsResponseDto;
import com.minjikim.codecobainbackend.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/wafer/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public StatsResponseDto getStats() {
        return statsService.getStats();
    }

    @GetMapping("/daily")
    public List<DailyStatsDto> getDailyStats(
            @RequestParam(defaultValue = "7") int days
    ) {
        return statsService.getDailyStats(days);
    }

    @GetMapping("/defect-distribution")
    public List<DefectDistributionDto> getDefectDistribution() {
        return statsService.getDefectDistribution();
    }
}