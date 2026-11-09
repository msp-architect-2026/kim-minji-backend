package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.request.SimulationRequest;
import com.minjikim.codecobainbackend.dto.response.SimulationResponse;
import com.minjikim.codecobainbackend.entity.*;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.repository.*;
import com.minjikim.codecobainbackend.util.TraceIds;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

// Lot·공정 이력·검사 결과를 AI 없이 직접 생성한다. 특정 설비를 거친 Lot에 특정 결함을 섞어 설비 이상을 재현한다
@Service
@Profile("local")
@RequiredArgsConstructor
public class SimulationService {

    private static final int WAFERS_PER_LOT = 25;
    private static final int HISTORY_DAYS = 14;
    private static final int LOT_DURATION_HOURS = 4;
    private static final double EXCURSION_DEFECT_RATE = 0.6;
    private static final double BASELINE_DEFECT_RATE = 0.08;
    private static final double LOW_CONFIDENCE_RATE = 0.05;

    private final LotRepository lots;
    private final WaferRepository wafers;
    private final WaferProcessHistoryRepository histories;
    private final InspectionRepository inspections;
    private final InspectionEventRepository events;
    private final EquipmentRepository equipments;
    private final ProcessStepRepository steps;
    private final InspectionLifecycle lifecycle;
    private final LotDispositionService disposition;

    @Transactional
    public SimulationResponse simulate(SimulationRequest request) {
        int lotCount = request.lots() == null ? 60 : request.lots();
        int excursionDays = request.excursionDays() == null ? 5 : request.excursionDays();
        String excursionEquipment = request.excursionEquipment() == null ? "ETCH-03" : request.excursionEquipment();
        String excursionDefect = request.excursionDefect() == null ? DefectType.EDGE_RING.getLabel() : request.excursionDefect();
        Random random = new Random(request.seed() == null ? 42L : request.seed());
        validate(lotCount, excursionEquipment, excursionDefect, excursionDays);

        List<ProcessStepEntity> route = steps.findAllByOrderBySeq();
        Map<String, List<EquipmentEntity>> equipmentByType = new HashMap<>();
        route.forEach(step -> equipmentByType.put(step.getCode(), equipments.findByType(step.getCode())));

        String batch = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();
        int excursionLots = 0;
        for (int index = 1; index <= lotCount; index++) {
            // 공정·검사 시각이 미래가 되지 않도록 Lot 처리 시간만큼 앞당겨 시작한다
            LocalDateTime startedAt = now.minusHours(LOT_DURATION_HOURS).minusMinutes(random.nextInt(HISTORY_DAYS * 24 * 60));
            LotEntity lot = lots.save(new LotEntity("SIM-%s-%03d".formatted(batch, index), "DRAM-16G", startedAt));

            Map<ProcessStepEntity, EquipmentEntity> lotRoute = new LinkedHashMap<>();
            for (ProcessStepEntity step : route) {
                List<EquipmentEntity> candidates = equipmentByType.get(step.getCode());
                lotRoute.put(step, candidates.get(random.nextInt(candidates.size())));
            }
            // 이상은 최근 excursionDays일 동안만 발생한다. 그 전 데이터가 관리도의 기준선이 된다
            boolean excursion = startedAt.isAfter(now.minusDays(excursionDays))
                    && lotRoute.values().stream().anyMatch(e -> e.getCode().equals(excursionEquipment));
            if (excursion) {
                excursionLots++;
            }
            for (int slotNo = 1; slotNo <= WAFERS_PER_LOT; slotNo++) {
                simulateWafer(lot, slotNo, lotRoute, startedAt, excursion, excursionDefect, random);
            }
            disposition.evaluate(lot.getId(), startedAt.plusHours(LOT_DURATION_HOURS));
        }
        return new SimulationResponse(lotCount, lotCount * WAFERS_PER_LOT, excursionLots, excursionEquipment, excursionDefect);
    }

    private void simulateWafer(LotEntity lot, int slotNo, Map<ProcessStepEntity, EquipmentEntity> lotRoute,
                               LocalDateTime startedAt, boolean excursion, String excursionDefect, Random random) {
        WaferEntity wafer = wafers.save(new WaferEntity(lot, slotNo));
        LocalDateTime cursor = startedAt.plusMinutes(slotNo);
        for (Map.Entry<ProcessStepEntity, EquipmentEntity> entry : lotRoute.entrySet()) {
            LocalDateTime endedAt = cursor.plusMinutes(30);
            histories.save(new WaferProcessHistoryEntity(wafer, entry.getKey(), entry.getValue(),
                    entry.getKey().getCode() + "-R1", cursor, endedAt));
            cursor = endedAt;
        }
        String prediction = pickPrediction(excursion, excursionDefect, random);
        double confidence = random.nextDouble() < LOW_CONFIDENCE_RATE
                ? 0.55 + random.nextDouble() * 0.24
                : 0.82 + random.nextDouble() * 0.17;
        recordInspection(wafer, slotNo, prediction, confidence, cursor);
    }

    private String pickPrediction(boolean excursion, String excursionDefect, Random random) {
        if (excursion && random.nextDouble() < EXCURSION_DEFECT_RATE) {
            return excursionDefect;
        }
        if (random.nextDouble() >= BASELINE_DEFECT_RATE) {
            return DefectType.NONE.getLabel();
        }
        DefectType[] types = DefectType.values();
        // NONE은 마지막 값이므로 제외하고 고른다
        return types[random.nextInt(types.length - 1)].getLabel();
    }

    private void recordInspection(WaferEntity wafer, int slotNo, String prediction, double confidence, LocalDateTime at) {
        String traceId = TraceIds.current();
        InspectionEntity inspection = inspections.save(new InspectionEntity(wafer, "simulated-%02d.png".formatted(slotNo), null, at));
        events.save(inspection.receivedEvent(traceId));
        events.save(inspection.markUploaded("simulated/%d/%02d.png".formatted(wafer.getLot().getId(), slotNo), traceId, at.plusSeconds(1)));
        events.save(inspection.startInference(traceId, at.plusSeconds(1)));
        events.save(inspection.complete(lifecycle.resultStatus(confidence), prediction, confidence,
                lifecycle.modelVersion(), traceId, at.plusSeconds(2)));
    }

    private void validate(int lotCount, String excursionEquipment, String excursionDefect, int excursionDays) {
        if (excursionDays < 1 || excursionDays > HISTORY_DAYS + 1) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, "excursionDays는 1~15여야 합니다.");
        }
        if (lotCount < 1 || lotCount > 200) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, "lots는 1~200이어야 합니다.");
        }
        if (equipments.findAll().stream().noneMatch(e -> e.getCode().equals(excursionEquipment))) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, "존재하지 않는 설비입니다: " + excursionEquipment);
        }
        if (!DefectType.isKnown(excursionDefect) || DefectType.NONE.getLabel().equalsIgnoreCase(excursionDefect)) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, "결함 유형이 올바르지 않습니다: " + excursionDefect);
        }
    }
}
