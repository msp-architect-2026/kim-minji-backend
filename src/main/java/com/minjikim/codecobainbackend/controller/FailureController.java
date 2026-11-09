package com.minjikim.codecobainbackend.controller;

import com.minjikim.codecobainbackend.dto.response.FailureItemResponse;
import com.minjikim.codecobainbackend.dto.response.FailureSummaryResponse;
import com.minjikim.codecobainbackend.service.FailureAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/failures")
@RequiredArgsConstructor
public class FailureController {

    private final FailureAnalysisService failureAnalysisService;

    @GetMapping("/summary")
    public FailureSummaryResponse summary(@RequestParam(defaultValue = "24") int hours) {
        return failureAnalysisService.summary(hours);
    }

    @GetMapping
    public Page<FailureItemResponse> list(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(required = false) String errorCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return failureAnalysisService.list(hours, errorCode, page, size);
    }
}
