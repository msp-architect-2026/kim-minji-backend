package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.EquipmentCommonalityResponse;
import com.minjikim.codecobainbackend.dto.response.PChartResponse;
import com.minjikim.codecobainbackend.entity.DefectType;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.repository.InspectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SpcService {

    private static final int MAX_DAYS = 90;
    private static final String NONE_LABEL = DefectType.NONE.getLabel();
    private static final String NO_EXCLUSION = "";

    private final InspectionRepository inspections;

    @Value("${spc.commonality.min-wafers}")
    private long minWafers;

    @Value("${spc.commonality.min-z-score}")
    private double minZScore;

    @Value("${spc.commonality.min-lift}")
    private double minLift;

    public PChartResponse pChart(int days, String defect, String equipment) {
        LocalDateTime from = fromDate(days);
        String defectFilter = normalizeDefect(defect);
        String equipmentFilter = equipment == null ? "" : equipment;
        List<SpcCalculator.Sample> samples = inspections.findPChartSamples(from, NONE_LABEL, defectFilter, equipmentFilter).stream()
                .map(row -> new SpcCalculator.Sample(row.getStatDate(), row.getTotal(), row.getDefect()))
                .toList();
        return SpcCalculator.pChart(samples);
    }

    public List<EquipmentCommonalityResponse> commonality(int days, String defect) {
        LocalDateTime from = fromDate(days);
        String defectFilter = normalizeDefect(defect);
        List<EquipmentCommonalityResponse> initial = analyze(from, defectFilter, List.of(NO_EXCLUSION));

        // Lot은 공정별 설비를 한 벌로 거치므로 원인 설비와 자주 겹친 설비도 신호가 뜬다.
        // 가장 강한 의심 설비를 확정하고 그 웨이퍼를 뺀 뒤 다시 검정해 겹침 효과를 걷어낸다
        List<String> confirmed = new ArrayList<>();
        List<EquipmentCommonalityResponse> current = initial;
        while (true) {
            Optional<String> strongest = current.stream()
                    .filter(EquipmentCommonalityResponse::suspect)
                    .map(EquipmentCommonalityResponse::equipmentCode)
                    .findFirst();
            if (strongest.isEmpty()) {
                break;
            }
            confirmed.add(strongest.get());
            current = analyze(from, defectFilter, confirmed);
        }
        return SpcCalculator.resolveConfounding(initial, confirmed);
    }

    private List<EquipmentCommonalityResponse> analyze(LocalDateTime from, String defect, List<String> excluded) {
        List<SpcCalculator.EquipmentCount> counts = inspections.findEquipmentCounts(from, NONE_LABEL, defect, excluded).stream()
                .map(row -> new SpcCalculator.EquipmentCount(row.getStepCode(), row.getEquipmentCode(), row.getTotal(), row.getDefect()))
                .toList();
        return SpcCalculator.commonality(counts, new SpcCalculator.SuspectRule(minWafers, minZScore, minLift));
    }

    private LocalDateTime fromDate(int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, "days는 1~90이어야 합니다.");
        }
        return LocalDate.now().minusDays(days - 1).atStartOfDay();
    }

    // 빈 문자열은 전체 결함 유형을 뜻한다
    private String normalizeDefect(String defect) {
        if (defect == null || defect.isBlank()) {
            return "";
        }
        if (!DefectType.isKnown(defect) || NONE_LABEL.equalsIgnoreCase(defect)) {
            throw new WaferException(ErrorCode.INVALID_REQUEST, "결함 유형이 올바르지 않습니다: " + defect);
        }
        return defect;
    }
}
