package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.AiPredictionResponse;
import com.minjikim.codecobainbackend.dto.response.InspectionResponse;
import com.minjikim.codecobainbackend.entity.*;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.repository.InspectionEventRepository;
import com.minjikim.codecobainbackend.repository.InspectionRepository;
import com.minjikim.codecobainbackend.repository.LotRepository;
import com.minjikim.codecobainbackend.repository.WaferRepository;
import com.minjikim.codecobainbackend.util.TraceIds;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

// 상태 전이마다 별도 트랜잭션으로 커밋한다. MinIO·AI 호출 중에는 DB 트랜잭션을 잡지 않는다
@Component
@RequiredArgsConstructor
public class InspectionLifecycle {

    private final InspectionRepository inspections;
    private final InspectionEventRepository events;
    private final WaferRepository wafers;
    private final LotDispositionService disposition;
    private final LotRepository lots;
    private final MeterRegistry meterRegistry;

    @Value("${inspection.review-confidence-threshold}")
    private double reviewConfidenceThreshold;

    @Value("${ai.model-version}")
    private String modelVersion;

    @Transactional
    public Long beginForWafer(Long lotId, int slotNo, String filename, String idempotencyKey) {
        WaferEntity wafer = wafers.findByLotIdAndSlotNo(lotId, slotNo)
                .orElseThrow(() -> new WaferException(ErrorCode.WAFER_NOT_FOUND));
        if (wafer.getLot().getStatus() != LotStatus.IN_PROGRESS) {
            throw new WaferException(ErrorCode.LOT_NOT_ACTIVE);
        }
        if (wafer.getStatus() == WaferStatus.INSPECTING) {
            throw new WaferException(ErrorCode.INSPECTION_IN_PROGRESS);
        }
        if (wafer.getStatus() == WaferStatus.REVIEW) {
            throw new WaferException(ErrorCode.INSPECTION_NEEDS_REVIEW);
        }
        return begin(wafer, filename, idempotencyKey);
    }

    @Transactional
    public Long beginWithoutWafer(String filename) {
        return begin(null, filename, null);
    }

    private Long begin(WaferEntity wafer, String filename, String idempotencyKey) {
        InspectionEntity inspection = inspections.saveAndFlush(
                new InspectionEntity(wafer, filename, idempotencyKey, LocalDateTime.now()));
        events.save(inspection.receivedEvent(TraceIds.current()));
        return inspection.getId();
    }

    @Transactional
    public void markUploaded(Long id, String imageKey) {
        events.save(load(id).markUploaded(imageKey, TraceIds.current(), LocalDateTime.now()));
    }

    @Transactional
    public void startInference(Long id) {
        events.save(load(id).startInference(TraceIds.current(), LocalDateTime.now()));
    }

    @Transactional
    public String startRetry(Long id) {
        InspectionEntity inspection = load(id);
        events.save(inspection.startRetry(TraceIds.current(), LocalDateTime.now()));
        return inspection.getImageKey();
    }

    // lotId는 웨이퍼 없는 검사면 null. 트랜잭션 밖에서 구해 넘긴다 (lockLot 주석)
    @Transactional
    public InspectionResponse complete(Long id, Long lotId, AiPredictionResponse result) {
        lockLot(lotId);
        InspectionEntity inspection = load(id);
        requireSameLot(inspection, lotId);
        events.save(inspection.complete(resultStatus(result.getConfidence()), result.getPrediction(),
                result.getConfidence(), modelVersion, TraceIds.current(), LocalDateTime.now()));
        InspectionResponse response = InspectionResponse.from(inspection);
        if (inspection.getWafer() != null) {
            disposition.evaluate(inspection.getWafer().getLot().getId(), LocalDateTime.now());
        }
        return response;
    }

    @Transactional
    public void fail(Long id, ErrorCode errorCode) {
        events.save(load(id).fail(errorCode.name(), TraceIds.current(), LocalDateTime.now()));
        meterRegistry.counter("mes.inspection.failures", "error_code", errorCode.name()).increment();
    }

    // 조회와 처리 사이에 검사가 진행됐을 수 있으므로 트랜잭션 안에서 다시 확인한다
    @Transactional
    public boolean failIfStuck(Long id, LocalDateTime cutoff) {
        InspectionEntity inspection = load(id);
        if (!inspection.getStatus().canTransitionTo(InspectionStatus.FAILED) || !inspection.getUpdatedAt().isBefore(cutoff)) {
            return false;
        }
        fail(id, ErrorCode.INSPECTION_STUCK);
        return true;
    }

    @Transactional(readOnly = true)
    public Long lotIdOf(Long id) {
        InspectionEntity inspection = load(id);
        return inspection.getWafer() == null ? null : inspection.getWafer().getLot().getId();
    }

    @Transactional(readOnly = true)
    public InspectionResponse view(Long id) {
        return InspectionResponse.from(load(id));
    }

    // 판정 확정은 웨이퍼를 바꾼 뒤 같은 Lot의 다른 웨이퍼를 읽어 Hold·완료를 정한다. 반드시 트랜잭션의 첫 문장으로 호출한다.
    // - 웨이퍼부터 잠그면 같은 Lot의 판정끼리 서로의 웨이퍼 잠금을 기다려 데드락이 난다 → Lot 행을 먼저 잠근다
    // - REPEATABLE READ의 읽기 스냅샷은 트랜잭션의 첫 일반 SELECT에서 만들어진다. 잠그기 전에 무엇이든 읽으면
    //   잠금을 기다리는 동안 커밋된 다른 판정이 Hold 집계에서 빠진다 → 잠금 전에는 아무것도 읽지 않는다
    public void lockLot(Long lotId) {
        if (lotId != null) {
            lots.findByIdForUpdate(lotId).orElseThrow(() -> new WaferException(ErrorCode.LOT_NOT_FOUND));
        }
    }

    public void requireSameLot(InspectionEntity inspection, Long lotId) {
        Long actual = inspection.getWafer() == null ? null : inspection.getWafer().getLot().getId();
        if (!Objects.equals(actual, lotId)) {
            throw new IllegalStateException("검사 %d의 Lot은 %s인데 %s을 잠갔습니다".formatted(inspection.getId(), actual, lotId));
        }
    }

    public InspectionStatus resultStatus(double confidence) {
        return confidence < reviewConfidenceThreshold ? InspectionStatus.NEED_REVIEW : InspectionStatus.DONE;
    }

    public String modelVersion() {
        return modelVersion;
    }

    private InspectionEntity load(Long id) {
        return inspections.findById(id).orElseThrow(() -> new WaferException(ErrorCode.INSPECTION_NOT_FOUND));
    }
}
