package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.EquipmentCommonalityResponse;
import com.minjikim.codecobainbackend.dto.response.PChartResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SpcCalculator {

    public static final String BEYOND_UCL = "BEYOND_UCL";
    public static final String RUN_ABOVE_CENTER = "RUN_ABOVE_CENTER";
    private static final int RUN_LENGTH = 8;
    private static final double SIGMA_LIMIT = 3.0;
    private static final Comparator<EquipmentCommonalityResponse> RANKING =
            Comparator.comparing(EquipmentCommonalityResponse::suspect).reversed()
                    .thenComparing(Comparator.comparingDouble(EquipmentCommonalityResponse::zScore).reversed());

    private SpcCalculator() {
    }

    public record Sample(String date, long inspected, long defects) {
    }

    public record EquipmentCount(String stepCode, String equipmentCode, long wafers, long defects) {
    }

    public record SuspectRule(long minWafers, double minZScore, double minLift) {
    }

    // p-chart: 표본 수가 날마다 달라 관리 한계도 점마다 다시 계산한다. 하한 이탈은 품질 개선 방향이므로 알림 대상에서 뺀다
    public static PChartResponse pChart(List<Sample> samples) {
        long totalInspected = samples.stream().mapToLong(Sample::inspected).sum();
        long totalDefects = samples.stream().mapToLong(Sample::defects).sum();
        double center = totalInspected == 0 ? 0 : (double) totalDefects / totalInspected;

        List<PChartResponse.Point> points = new ArrayList<>();
        int run = 0;
        for (Sample sample : samples) {
            double rate = (double) sample.defects() / sample.inspected();
            double margin = SIGMA_LIMIT * Math.sqrt(center * (1 - center) / sample.inspected());
            double ucl = Math.min(1, center + margin);
            double lcl = Math.max(0, center - margin);

            run = rate > center ? run + 1 : 0;
            List<String> violations = new ArrayList<>();
            if (rate > ucl) {
                violations.add(BEYOND_UCL);
            }
            if (run >= RUN_LENGTH) {
                violations.add(RUN_ABOVE_CENTER);
            }
            points.add(new PChartResponse.Point(sample.date(), sample.inspected(), sample.defects(), rate, ucl, lcl, violations));
        }
        return new PChartResponse(center, points);
    }

    // 같은 공정의 다른 설비와 결함률을 비교한다(두 비율 z 검정). 설비가 하나뿐인 공정은 비교 대상이 없어 의심으로 표시하지 않는다
    public static List<EquipmentCommonalityResponse> commonality(List<EquipmentCount> counts, SuspectRule rule) {
        Map<String, List<EquipmentCount>> byStep = counts.stream().collect(Collectors.groupingBy(EquipmentCount::stepCode));
        return counts.stream()
                .map(count -> compare(count, byStep.get(count.stepCode()), rule))
                .sorted(RANKING)
                .toList();
    }

    // 처음에 의심으로 나왔지만 확정 설비의 웨이퍼를 뺀 뒤 신호가 사라진 설비는, 원인이 아니라 같은 Lot을 처리한 설비로 표시한다
    public static List<EquipmentCommonalityResponse> resolveConfounding(List<EquipmentCommonalityResponse> initial, List<String> confirmed) {
        return initial.stream()
                .map(row -> {
                    boolean suspect = confirmed.contains(row.equipmentCode());
                    String confoundedWith = row.suspect() && !suspect ? confirmed.get(0) : null;
                    return new EquipmentCommonalityResponse(row.stepCode(), row.equipmentCode(), row.wafers(), row.defects(),
                            row.defectRate(), row.otherWafers(), row.otherDefectRate(), row.lift(), row.zScore(), suspect, confoundedWith);
                })
                .sorted(RANKING)
                .toList();
    }

    private static EquipmentCommonalityResponse compare(EquipmentCount target, List<EquipmentCount> step, SuspectRule rule) {
        long otherWafers = step.stream().mapToLong(EquipmentCount::wafers).sum() - target.wafers();
        long otherDefects = step.stream().mapToLong(EquipmentCount::defects).sum() - target.defects();
        double rate = (double) target.defects() / target.wafers();
        double otherRate = otherWafers == 0 ? 0 : (double) otherDefects / otherWafers;
        Double lift = otherRate == 0 ? null : rate / otherRate;
        double z = zScore(target.wafers(), target.defects(), otherWafers, otherDefects);

        boolean suspect = otherWafers > 0
                && target.wafers() >= rule.minWafers()
                && z >= rule.minZScore()
                && (lift == null || lift >= rule.minLift());
        return new EquipmentCommonalityResponse(target.stepCode(), target.equipmentCode(), target.wafers(), target.defects(),
                rate, otherWafers, otherRate, lift, z, suspect, null);
    }

    private static double zScore(long n1, long d1, long n2, long d2) {
        if (n2 == 0) {
            return 0;
        }
        double pooled = (double) (d1 + d2) / (n1 + n2);
        double standardError = Math.sqrt(pooled * (1 - pooled) * (1.0 / n1 + 1.0 / n2));
        return standardError == 0 ? 0 : ((double) d1 / n1 - (double) d2 / n2) / standardError;
    }
}
