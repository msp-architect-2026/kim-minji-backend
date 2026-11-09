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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 상태 전이마다 별도 트랜잭션으로 커밋한다. MinIO·AI 호출 중에는 DB 트랜잭션을 잡지 않는다
@Component
@RequiredArgsConstructor
public class InspectionLifecycle {

    private static final List<WaferStatus> FINISHED_WAFER_STATUSES = List.of(WaferStatus.PASS, WaferStatus.DEFECT);

    private final InspectionRepository inspections;
    private final InspectionEventRepository events;
    private final WaferRepository wafers;
    private final LotRepository lots;

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

    @Transactional
    public InspectionResponse complete(Long id, AiPredictionResponse result) {
        InspectionEntity inspection = load(id);
        events.save(inspection.complete(resultStatus(result.getConfidence()), result.getPrediction(),
                result.getConfidence(), modelVersion, TraceIds.current(), LocalDateTime.now()));
        InspectionResponse response = InspectionResponse.from(inspection);
        if (inspection.getWafer() != null) {
            completeLotIfFinished(inspection.getWafer().getLot().getId());
        }
        return response;
    }

    @Transactional
    public void fail(Long id, ErrorCode errorCode) {
        events.save(load(id).fail(errorCode.name(), TraceIds.current(), LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public InspectionResponse view(Long id) {
        return InspectionResponse.from(load(id));
    }

    public InspectionStatus resultStatus(double confidence) {
        return confidence < reviewConfidenceThreshold ? InspectionStatus.NEED_REVIEW : InspectionStatus.DONE;
    }

    public String modelVersion() {
        return modelVersion;
    }

    public void completeLotIfFinished(Long lotId) {
        lots.updateStatusIfAllWafersIn(lotId, LotStatus.COMPLETED, FINISHED_WAFER_STATUSES);
    }

    private InspectionEntity load(Long id) {
        return inspections.findById(id).orElseThrow(() -> new WaferException(ErrorCode.INSPECTION_NOT_FOUND));
    }
}
