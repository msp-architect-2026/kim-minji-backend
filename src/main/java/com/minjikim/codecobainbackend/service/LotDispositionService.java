package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.entity.*;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.repository.LotHoldRepository;
import com.minjikim.codecobainbackend.repository.LotRepository;
import com.minjikim.codecobainbackend.repository.WaferRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Lot 상태는 조건부 UPDATE로만 바꾼다. 같은 Lot의 웨이퍼가 동시에 판정돼도 Hold 이력은 한 건만 생긴다
@Service
@RequiredArgsConstructor
public class LotDispositionService {

    private static final List<WaferStatus> FINISHED_WAFER_STATUSES = List.of(WaferStatus.PASS, WaferStatus.DEFECT);
    private static final String SYSTEM_OPERATOR = "SYSTEM";

    private final LotRepository lots;
    private final LotHoldRepository holds;
    private final WaferRepository wafers;
    private final MeterRegistry meterRegistry;

    @Value("${lot.hold.min-judged-wafers}")
    private int minJudgedWafers;

    @Value("${lot.hold.baseline-defect-rate}")
    private double baselineDefectRate;

    @Value("${lot.hold.significance}")
    private double significance;

    // 웨이퍼 판정이 확정될 때마다 호출한다
    @Transactional
    public void evaluate(Long lotId, LocalDateTime at) {
        Map<WaferStatus, Long> counts = new EnumMap<>(WaferStatus.class);
        wafers.countByLotIdsAndStatus(List.of(lotId)).forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        long defect = counts.getOrDefault(WaferStatus.DEFECT, 0L);
        long judged = defect + counts.getOrDefault(WaferStatus.PASS, 0L);

        // 결함률만 보면 표본이 작을 때 정상 Lot도 우연히 기준을 넘는다(5장 중 2장 = 40%).
        // 기준 결함률에서 이만큼의 결함이 우연히 나올 확률이 유의수준보다 작을 때만 Hold한다.
        // Release는 엔지니어가 이상 없다고 판단한 것이므로 같은 Lot을 자동으로 다시 잡지 않는다
        double pValue = binomialTail(judged, defect, baselineDefectRate);
        if (judged >= minJudgedWafers
                && pValue < significance
                && !holds.existsByLotIdAndSource(lotId, HoldSource.AUTO)) {
            String reason = "결함률 이상: %d/%d (기준 %.0f%%, p=%.1e)".formatted(defect, judged, baselineDefectRate * 100, pValue);
            placeHold(lotId, HoldSource.AUTO, reason, SYSTEM_OPERATOR, at);
        }
        completeIfFinished(lotId);
    }

    // P(X >= defects), X ~ Binomial(n, p)
    static double binomialTail(long n, long defects, double p) {
        double pmf = Math.pow(1 - p, n);
        double below = 0;
        for (long k = 0; k < defects; k++) {
            below += pmf;
            pmf = pmf * (n - k) / (k + 1) * p / (1 - p);
        }
        return Math.max(0, 1 - below);
    }

    @Transactional
    public void holdManually(Long lotId, String reason, String operator) {
        requireLot(lotId);
        requireText(reason, "reason");
        requireText(operator, "operator");
        if (!placeHold(lotId, HoldSource.MANUAL, reason, operator, LocalDateTime.now())) {
            throw new WaferException(ErrorCode.LOT_NOT_ACTIVE);
        }
    }

    @Transactional
    public void release(Long lotId, String comment, String operator) {
        requireLot(lotId);
        requireText(comment, "comment");
        requireText(operator, "operator");
        if (lots.transitionStatus(lotId, LotStatus.HOLD, LotStatus.IN_PROGRESS) == 0) {
            throw new WaferException(ErrorCode.LOT_NOT_ON_HOLD);
        }
        holds.findFirstByLotIdAndReleasedAtIsNull(lotId)
                .orElseThrow(() -> new IllegalStateException("Hold 상태인 Lot에 활성 Hold 이력이 없습니다: " + lotId))
                .release(operator, comment, LocalDateTime.now());
        completeIfFinished(lotId);
    }

    private boolean placeHold(Long lotId, HoldSource source, String reason, String operator, LocalDateTime at) {
        if (lots.transitionStatus(lotId, LotStatus.IN_PROGRESS, LotStatus.HOLD) == 0) {
            return false;
        }
        holds.save(new LotHoldEntity(lots.getReferenceById(lotId), source, reason, operator, at));
        meterRegistry.counter("mes.lot.holds", "source", source.name()).increment();
        return true;
    }

    private void completeIfFinished(Long lotId) {
        lots.updateStatusIfAllWafersIn(lotId, LotStatus.IN_PROGRESS, LotStatus.COMPLETED, FINISHED_WAFER_STATUSES);
    }

    private void requireLot(Long lotId) {
        if (!lots.existsById(lotId)) {
            throw new WaferException(ErrorCode.LOT_NOT_FOUND);
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, field + "는 1~255자여야 합니다.");
        }
    }
}
