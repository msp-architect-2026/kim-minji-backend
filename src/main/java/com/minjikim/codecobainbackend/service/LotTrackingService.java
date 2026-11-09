package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.request.TrackInRequest;
import com.minjikim.codecobainbackend.dto.request.TrackOutRequest;
import com.minjikim.codecobainbackend.dto.response.LotRouteResponse;
import com.minjikim.codecobainbackend.dto.response.LotRouteResponse.StepStatus;
import com.minjikim.codecobainbackend.entity.*;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 설비 이벤트는 Lot 단위로 받아 Lot의 모든 웨이퍼에 이력을 남긴다. Lot 행을 잠가 같은 Lot의 이벤트를 순서대로 처리한다
@Service
@RequiredArgsConstructor
public class LotTrackingService {

    private static final int MAX_RECIPE_LENGTH = 50;

    private final LotRepository lots;
    private final WaferRepository wafers;
    private final WaferProcessHistoryRepository histories;
    private final ProcessStepRepository steps;
    private final EquipmentRepository equipments;

    @Transactional
    public LotRouteResponse trackIn(Long lotId, TrackInRequest request) {
        LotEntity lot = lockLot(lotId);
        ProcessStepEntity step = findStep(request.stepCode());
        EquipmentEntity equipment = equipments.findByCode(request.equipmentCode())
                .orElseThrow(() -> new WaferException(ErrorCode.EQUIPMENT_NOT_FOUND));
        validateRecipe(request.recipe());
        if (!equipment.getType().equals(step.getCode())) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, equipment.getCode() + "는 " + step.getCode() + " 공정 설비가 아닙니다.");
        }

        List<WaferProcessHistoryEntity> history = histories.findByLotIdWithDetails(lotId);
        Optional<WaferProcessHistoryEntity> open = findOpen(history);
        if (open.isPresent()) {
            // 설비가 같은 이벤트를 재전송해도 이력이 중복되지 않는다
            if (isSameTrackIn(open.get(), step, equipment, request.recipe())) {
                return buildRoute(lotId, history);
            }
            throw new WaferException(ErrorCode.LOT_ALREADY_TRACKED_IN);
        }
        if (lot.getStatus() != LotStatus.IN_PROGRESS) {
            throw new WaferException(ErrorCode.LOT_NOT_ACTIVE);
        }
        requireNextStep(step, history);

        LocalDateTime now = LocalDateTime.now();
        histories.saveAll(wafers.findByLotIdOrderBySlotNo(lotId).stream()
                .map(wafer -> new WaferProcessHistoryEntity(wafer, step, equipment, request.recipe(), now, null))
                .toList());
        return buildRoute(lotId, histories.findByLotIdWithDetails(lotId));
    }

    // Hold가 걸려도 이미 설비에 들어간 Lot은 꺼낼 수 있어야 하므로 TrackOut은 막지 않는다
    @Transactional
    public LotRouteResponse trackOut(Long lotId, TrackOutRequest request) {
        lockLot(lotId);
        ProcessStepEntity step = findStep(request.stepCode());
        List<WaferProcessHistoryEntity> history = histories.findByLotIdWithDetails(lotId);

        Optional<WaferProcessHistoryEntity> open = findOpen(history);
        if (open.isEmpty() && isDone(step, history)) {
            return buildRoute(lotId, history);
        }
        if (open.isEmpty() || !open.get().getStep().getId().equals(step.getId())) {
            throw new WaferException(ErrorCode.LOT_NOT_TRACKED_IN);
        }
        histories.closeOpen(lotId, step.getId(), LocalDateTime.now());
        return buildRoute(lotId, histories.findByLotIdWithDetails(lotId));
    }

    @Transactional(readOnly = true)
    public LotRouteResponse route(Long lotId) {
        if (!lots.existsById(lotId)) {
            throw new WaferException(ErrorCode.LOT_NOT_FOUND);
        }
        return buildRoute(lotId, histories.findByLotIdWithDetails(lotId));
    }

    private LotEntity lockLot(Long lotId) {
        return lots.findByIdForUpdate(lotId).orElseThrow(() -> new WaferException(ErrorCode.LOT_NOT_FOUND));
    }

    private ProcessStepEntity findStep(String code) {
        return steps.findByCode(code).orElseThrow(() -> new WaferException(ErrorCode.STEP_NOT_FOUND));
    }

    private void validateRecipe(String recipe) {
        if (recipe == null || recipe.isBlank() || recipe.length() > MAX_RECIPE_LENGTH) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, "recipe는 1~50자여야 합니다.");
        }
    }

    private void requireNextStep(ProcessStepEntity step, List<WaferProcessHistoryEntity> history) {
        ProcessStepEntity next = steps.findAllByOrderBySeq().stream()
                .filter(candidate -> !isDone(candidate, history))
                .findFirst()
                .orElseThrow(() -> new WaferException(ErrorCode.ROUTE_VIOLATION, "모든 공정이 끝난 Lot입니다."));
        if (!next.getId().equals(step.getId())) {
            throw new WaferException(ErrorCode.ROUTE_VIOLATION, "다음 공정은 " + next.getCode() + "입니다.");
        }
    }

    private Optional<WaferProcessHistoryEntity> findOpen(List<WaferProcessHistoryEntity> history) {
        return history.stream().filter(row -> row.getEndedAt() == null).findFirst();
    }

    private boolean isDone(ProcessStepEntity step, List<WaferProcessHistoryEntity> history) {
        return history.stream().anyMatch(row -> row.getStep().getId().equals(step.getId()) && row.getEndedAt() != null);
    }

    private boolean isSameTrackIn(WaferProcessHistoryEntity open, ProcessStepEntity step, EquipmentEntity equipment, String recipe) {
        return open.getStep().getId().equals(step.getId())
                && open.getEquipment().getId().equals(equipment.getId())
                && open.getRecipe().equals(recipe);
    }

    private LotRouteResponse buildRoute(Long lotId, List<WaferProcessHistoryEntity> history) {
        List<ProcessStepEntity> route = steps.findAllByOrderBySeq();
        List<LotRouteResponse.Step> rows = route.stream().map(step -> toStep(step, history)).toList();
        String current = rows.stream().filter(row -> row.status() == StepStatus.IN_PROGRESS)
                .map(LotRouteResponse.Step::stepCode).findFirst().orElse(null);
        String next = current != null ? null : rows.stream().filter(row -> row.status() == StepStatus.PENDING)
                .map(LotRouteResponse.Step::stepCode).findFirst().orElse(null);
        List<String> available = next == null ? List.of()
                : equipments.findByType(next).stream().map(EquipmentEntity::getCode).toList();
        return new LotRouteResponse(lotId, current, next, available, rows);
    }

    private LotRouteResponse.Step toStep(ProcessStepEntity step, List<WaferProcessHistoryEntity> history) {
        Optional<WaferProcessHistoryEntity> row = history.stream()
                .filter(candidate -> candidate.getStep().getId().equals(step.getId()))
                .findFirst();
        if (row.isEmpty()) {
            return new LotRouteResponse.Step(step.getCode(), step.getName(), StepStatus.PENDING, null, null, null, null);
        }
        WaferProcessHistoryEntity found = row.get();
        StepStatus status = found.getEndedAt() == null ? StepStatus.IN_PROGRESS : StepStatus.DONE;
        return new LotRouteResponse.Step(step.getCode(), step.getName(), status, found.getEquipment().getCode(),
                found.getRecipe(), found.getStartedAt(), found.getEndedAt());
    }
}
