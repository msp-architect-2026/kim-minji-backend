package com.minjikim.codecobainbackend.controller;

import com.minjikim.codecobainbackend.dto.request.ReviewRequest;
import com.minjikim.codecobainbackend.dto.response.InspectionEventResponse;
import com.minjikim.codecobainbackend.dto.response.InspectionResponse;
import com.minjikim.codecobainbackend.dto.response.PendingReviewResponse;
import com.minjikim.codecobainbackend.service.InspectionReviewService;
import com.minjikim.codecobainbackend.service.InspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inspections")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;
    private final InspectionReviewService reviewService;

    @GetMapping("/pending-reviews")
    public Page<PendingReviewResponse> pendingReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return reviewService.pending(page, size);
    }

    @PostMapping("/{id}/review")
    public InspectionResponse review(@PathVariable Long id, @RequestBody ReviewRequest request) {
        return reviewService.review(id, request);
    }

    @PostMapping("/{id}/retry")
    public InspectionResponse retry(@PathVariable Long id) {
        return inspectionService.retry(id);
    }

    @GetMapping("/{id}/events")
    public List<InspectionEventResponse> events(@PathVariable Long id) {
        return inspectionService.findEvents(id);
    }
}
