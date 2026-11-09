package com.minjikim.codecobainbackend.controller;

import com.minjikim.codecobainbackend.dto.response.InspectionEventResponse;
import com.minjikim.codecobainbackend.dto.response.InspectionResponse;
import com.minjikim.codecobainbackend.service.InspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inspections")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;

    @PostMapping("/{id}/retry")
    public InspectionResponse retry(@PathVariable Long id) {
        return inspectionService.retry(id);
    }

    @GetMapping("/{id}/events")
    public List<InspectionEventResponse> events(@PathVariable Long id) {
        return inspectionService.findEvents(id);
    }
}
