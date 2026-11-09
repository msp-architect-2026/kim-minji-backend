package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.request.LotCreateRequest;
import com.minjikim.codecobainbackend.dto.response.InspectionResponse;
import com.minjikim.codecobainbackend.dto.response.LotDetailResponse;
import com.minjikim.codecobainbackend.dto.response.LotSummaryResponse;
import com.minjikim.codecobainbackend.dto.response.ProcessHistoryResponse;
import com.minjikim.codecobainbackend.entity.InspectionEntity;
import com.minjikim.codecobainbackend.entity.LotEntity;
import com.minjikim.codecobainbackend.entity.WaferEntity;
import com.minjikim.codecobainbackend.entity.WaferStatus;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.repository.InspectionRepository;
import com.minjikim.codecobainbackend.repository.LotRepository;
import com.minjikim.codecobainbackend.repository.WaferProcessHistoryRepository;
import com.minjikim.codecobainbackend.repository.WaferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class LotService {

    private static final int MAX_WAFERS_PER_LOT = 25;
    private static final int MAX_CODE_LENGTH = 50;

    private final LotRepository lots;
    private final WaferRepository wafers;
    private final InspectionRepository inspections;
    private final WaferProcessHistoryRepository histories;

    @Transactional
    public LotDetailResponse create(LotCreateRequest request) {
        validate(request);
        if (lots.existsByLotNo(request.lotNo())) {
            throw new WaferException(ErrorCode.LOT_ALREADY_EXISTS);
        }
        LotEntity lot;
        try {
            lot = lots.saveAndFlush(new LotEntity(request.lotNo(), request.product(), LocalDateTime.now()));
        } catch (DataIntegrityViolationException e) {
            throw new WaferException(ErrorCode.LOT_ALREADY_EXISTS, e);
        }
        int waferCount = request.waferCount() == null ? MAX_WAFERS_PER_LOT : request.waferCount();
        List<WaferEntity> created = wafers.saveAll(IntStream.rangeClosed(1, waferCount)
                .mapToObj(slotNo -> new WaferEntity(lot, slotNo))
                .toList());
        return toDetail(lot, created, Map.of());
    }

    @Transactional(readOnly = true)
    public Page<LotSummaryResponse> list(int page, int size) {
        Page<LotEntity> result = lots.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        List<Long> lotIds = result.stream().map(LotEntity::getId).toList();

        Map<Long, Map<WaferStatus, Long>> counts = new HashMap<>();
        if (!lotIds.isEmpty()) {
            wafers.countByLotIdsAndStatus(lotIds).forEach(row -> counts
                    .computeIfAbsent(row.getLotId(), id -> new EnumMap<>(WaferStatus.class))
                    .put(row.getStatus(), row.getTotal()));
        }
        return result.map(lot -> toSummary(lot, counts.getOrDefault(lot.getId(), Map.of())));
    }

    @Transactional(readOnly = true)
    public LotDetailResponse detail(Long id) {
        LotEntity lot = lots.findById(id).orElseThrow(() -> new WaferException(ErrorCode.LOT_NOT_FOUND));
        List<WaferEntity> lotWafers = wafers.findByLotIdOrderBySlotNo(id);

        Map<Long, InspectionEntity> latest = new HashMap<>();
        if (!lotWafers.isEmpty()) {
            inspections.findByWaferIdsOrderById(lotWafers.stream().map(WaferEntity::getId).toList())
                    .forEach(inspection -> latest.put(inspection.getWafer().getId(), inspection));
        }
        return toDetail(lot, lotWafers, latest);
    }

    @Transactional(readOnly = true)
    public List<ProcessHistoryResponse> processHistory(Long waferId) {
        if (!wafers.existsById(waferId)) {
            throw new WaferException(ErrorCode.WAFER_NOT_FOUND);
        }
        return histories.findByWaferIdWithDetails(waferId).stream().map(ProcessHistoryResponse::from).toList();
    }

    private void validate(LotCreateRequest request) {
        if (isInvalidCode(request.lotNo()) || isInvalidCode(request.product())) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, "lotNo와 product는 1~50자여야 합니다.");
        }
        Integer waferCount = request.waferCount();
        if (waferCount != null && (waferCount < 1 || waferCount > MAX_WAFERS_PER_LOT)) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, "waferCount는 1~25여야 합니다.");
        }
    }

    private boolean isInvalidCode(String value) {
        return value == null || value.isBlank() || value.length() > MAX_CODE_LENGTH;
    }

    private LotSummaryResponse toSummary(LotEntity lot, Map<WaferStatus, Long> counts) {
        return new LotSummaryResponse(
                lot.getId(), lot.getLotNo(), lot.getProduct(), lot.getStatus(),
                counts.values().stream().mapToLong(Long::longValue).sum(),
                counts.getOrDefault(WaferStatus.PASS, 0L),
                counts.getOrDefault(WaferStatus.DEFECT, 0L),
                counts.getOrDefault(WaferStatus.REVIEW, 0L),
                counts.getOrDefault(WaferStatus.FAILED, 0L),
                lot.getCreatedAt()
        );
    }

    private LotDetailResponse toDetail(LotEntity lot, List<WaferEntity> lotWafers, Map<Long, InspectionEntity> latest) {
        List<LotDetailResponse.WaferSlot> slots = lotWafers.stream()
                .map(wafer -> {
                    InspectionEntity inspection = latest.get(wafer.getId());
                    return new LotDetailResponse.WaferSlot(wafer.getId(), wafer.getSlotNo(), wafer.getStatus(),
                            inspection == null ? null : InspectionResponse.from(inspection));
                })
                .toList();
        return new LotDetailResponse(lot.getId(), lot.getLotNo(), lot.getProduct(), lot.getStatus(), lot.getCreatedAt(), slots);
    }
}
