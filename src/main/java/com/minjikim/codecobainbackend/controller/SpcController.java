package com.minjikim.codecobainbackend.controller;

import com.minjikim.codecobainbackend.dto.response.EquipmentCommonalityResponse;
import com.minjikim.codecobainbackend.dto.response.PChartResponse;
import com.minjikim.codecobainbackend.service.SpcService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/spc")
@RequiredArgsConstructor
public class SpcController {

    private final SpcService spcService;

    @GetMapping("/p-chart")
    public PChartResponse pChart(
            @RequestParam(defaultValue = "14") int days,
            @RequestParam(required = false) String defect,
            @RequestParam(required = false) String equipment
    ) {
        return spcService.pChart(days, defect, equipment);
    }

    @GetMapping("/commonality")
    public List<EquipmentCommonalityResponse> commonality(
            @RequestParam(defaultValue = "14") int days,
            @RequestParam(required = false) String defect
    ) {
        return spcService.commonality(days, defect);
    }
}
