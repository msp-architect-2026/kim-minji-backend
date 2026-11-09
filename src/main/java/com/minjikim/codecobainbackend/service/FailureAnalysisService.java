package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.FailureItemResponse;
import com.minjikim.codecobainbackend.dto.response.FailureSummaryResponse;
import com.minjikim.codecobainbackend.entity.InspectionStatus;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.repository.InspectionEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FailureAnalysisService {

    private static final int MAX_HOURS = 720;

    private final InspectionEventRepository events;

    @Transactional(readOnly = true)
    public FailureSummaryResponse summary(int hours) {
        List<FailureSummaryResponse.Row> rows = events.summarizeFailures(from(hours), InspectionStatus.FAILED).stream()
                .map(row -> new FailureSummaryResponse.Row(FailureItemResponse.stageOf(row.getFromStatus()), row.getErrorCode(), row.getTotal(), row.getUnresolved()))
                .toList();
        return new FailureSummaryResponse(
                hours,
                rows.stream().mapToLong(FailureSummaryResponse.Row::total).sum(),
                rows.stream().mapToLong(FailureSummaryResponse.Row::unresolved).sum(),
                rows
        );
    }

    @Transactional(readOnly = true)
    public Page<FailureItemResponse> list(int hours, String errorCode, int page, int size) {
        return events.findFailures(from(hours), InspectionStatus.FAILED, errorCode == null ? "" : errorCode, PageRequest.of(page, size))
                .map(FailureItemResponse::from);
    }

    private LocalDateTime from(int hours) {
        if (hours < 1 || hours > MAX_HOURS) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, "hours는 1~720이어야 합니다.");
        }
        return LocalDateTime.now().minusHours(hours);
    }
}
