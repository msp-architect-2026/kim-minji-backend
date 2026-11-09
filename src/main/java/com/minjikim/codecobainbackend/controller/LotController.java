package com.minjikim.codecobainbackend.controller;

import com.minjikim.codecobainbackend.dto.request.LotCreateRequest;
import com.minjikim.codecobainbackend.dto.request.LotHoldRequest;
import com.minjikim.codecobainbackend.dto.request.LotReleaseRequest;
import com.minjikim.codecobainbackend.dto.request.TrackInRequest;
import com.minjikim.codecobainbackend.dto.request.TrackOutRequest;
import com.minjikim.codecobainbackend.dto.response.InspectionResponse;
import com.minjikim.codecobainbackend.dto.response.LotRouteResponse;
import com.minjikim.codecobainbackend.dto.response.LotDetailResponse;
import com.minjikim.codecobainbackend.dto.response.LotSummaryResponse;
import com.minjikim.codecobainbackend.dto.response.ProcessHistoryResponse;
import com.minjikim.codecobainbackend.service.InspectionService;
import com.minjikim.codecobainbackend.service.LotDispositionService;
import com.minjikim.codecobainbackend.service.LotService;
import com.minjikim.codecobainbackend.service.LotTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LotController {

    private final LotService lotService;
    private final InspectionService inspectionService;
    private final LotDispositionService disposition;
    private final LotTrackingService tracking;

    @PostMapping("/lots")
    @ResponseStatus(HttpStatus.CREATED)
    public LotDetailResponse create(@RequestBody LotCreateRequest request) {
        return lotService.create(request);
    }

    @GetMapping("/lots")
    public Page<LotSummaryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return lotService.list(page, size);
    }

    @GetMapping("/lots/{id}")
    public LotDetailResponse detail(@PathVariable Long id) {
        return lotService.detail(id);
    }

    @GetMapping("/lots/{id}/route")
    public LotRouteResponse route(@PathVariable Long id) {
        return tracking.route(id);
    }

    @PostMapping("/lots/{id}/track-in")
    public LotRouteResponse trackIn(@PathVariable Long id, @RequestBody TrackInRequest request) {
        return tracking.trackIn(id, request);
    }

    @PostMapping("/lots/{id}/track-out")
    public LotRouteResponse trackOut(@PathVariable Long id, @RequestBody TrackOutRequest request) {
        return tracking.trackOut(id, request);
    }

    @PostMapping("/lots/{id}/hold")
    public LotDetailResponse hold(@PathVariable Long id, @RequestBody LotHoldRequest request) {
        disposition.holdManually(id, request.reason(), request.operator());
        return lotService.detail(id);
    }

    @PostMapping("/lots/{id}/release")
    public LotDetailResponse release(@PathVariable Long id, @RequestBody LotReleaseRequest request) {
        disposition.release(id, request.comment(), request.operator());
        return lotService.detail(id);
    }

    @PostMapping("/lots/{id}/wafers/{slotNo}/inspections")
    public InspectionResponse inspect(
            @PathVariable Long id,
            @PathVariable int slotNo,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return inspectionService.inspectWafer(id, slotNo, file, idempotencyKey);
    }

    @GetMapping("/wafers/{id}/process-history")
    public List<ProcessHistoryResponse> processHistory(@PathVariable Long id) {
        return lotService.processHistory(id);
    }
}
