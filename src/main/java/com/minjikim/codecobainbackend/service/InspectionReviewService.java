package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.request.ReviewRequest;
import com.minjikim.codecobainbackend.dto.response.InspectionResponse;
import com.minjikim.codecobainbackend.dto.response.PendingReviewResponse;
import com.minjikim.codecobainbackend.entity.DefectType;
import com.minjikim.codecobainbackend.entity.InspectionEntity;
import com.minjikim.codecobainbackend.entity.InspectionReviewEntity;
import com.minjikim.codecobainbackend.entity.InspectionStatus;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.repository.InspectionEventRepository;
import com.minjikim.codecobainbackend.repository.InspectionRepository;
import com.minjikim.codecobainbackend.repository.InspectionReviewRepository;
import com.minjikim.codecobainbackend.util.TraceIds;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InspectionReviewService {

    private static final int MAX_REVIEWER_LENGTH = 50;
    private static final int MAX_COMMENT_LENGTH = 255;

    private final InspectionRepository inspections;
    private final InspectionEventRepository events;
    private final InspectionReviewRepository reviews;
    private final LotDispositionService disposition;

    @Transactional(readOnly = true)
    public Page<PendingReviewResponse> pending(int page, int size) {
        return inspections.findByStatusWithLot(InspectionStatus.NEED_REVIEW, PageRequest.of(page, size))
                .map(PendingReviewResponse::from);
    }

    @Transactional
    public InspectionResponse review(Long id, ReviewRequest request) {
        validate(request);
        InspectionEntity inspection = inspections.findById(id)
                .orElseThrow(() -> new WaferException(ErrorCode.INSPECTION_NOT_FOUND));
        if (inspection.getStatus() != InspectionStatus.NEED_REVIEW) {
            throw new WaferException(ErrorCode.INSPECTION_NOT_REVIEWABLE);
        }
        LocalDateTime now = LocalDateTime.now();
        reviews.save(new InspectionReviewEntity(inspection, request.finalPrediction(), request.reviewer(), request.comment(), now));
        events.save(inspection.confirmReview(request.finalPrediction(), TraceIds.current(), now));

        InspectionResponse response = InspectionResponse.from(inspection);
        if (inspection.getWafer() != null) {
            disposition.evaluate(inspection.getWafer().getLot().getId(), now);
        }
        return response;
    }

    private void validate(ReviewRequest request) {
        if (!DefectType.isKnown(request.finalPrediction())) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, "결함 유형이 올바르지 않습니다: " + request.finalPrediction());
        }
        if (request.reviewer() == null || request.reviewer().isBlank() || request.reviewer().length() > MAX_REVIEWER_LENGTH) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, "reviewer는 1~50자여야 합니다.");
        }
        if (request.comment() != null && request.comment().length() > MAX_COMMENT_LENGTH) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, "comment는 255자 이하여야 합니다.");
        }
    }
}
