package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.EquipmentCommonalityResponse;
import com.minjikim.codecobainbackend.dto.response.PChartResponse;
import com.minjikim.codecobainbackend.service.SpcCalculator.EquipmentCount;
import com.minjikim.codecobainbackend.service.SpcCalculator.Sample;
import com.minjikim.codecobainbackend.service.SpcCalculator.SuspectRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SpcCalculatorTest {

    private static final SuspectRule RULE = new SuspectRule(30, 3.0, 2.0);

    @Test
    void pChartUsesPerPointLimits() {
        PChartResponse chart = SpcCalculator.pChart(List.of(new Sample("d1", 100, 10), new Sample("d2", 400, 40)));

        assertThat(chart.centerLine()).isCloseTo(0.1, within(1e-9));
        assertThat(chart.points().get(0).ucl()).isCloseTo(0.19, within(1e-9));
        assertThat(chart.points().get(1).ucl()).isCloseTo(0.145, within(1e-9));
        assertThat(chart.points().get(0).lcl()).isCloseTo(0.01, within(1e-9));
    }

    @Test
    void pointAboveUclIsFlagged() {
        List<Sample> samples = new ArrayList<>();
        for (int day = 0; day < 9; day++) {
            samples.add(new Sample("d" + day, 100, 5));
        }
        samples.add(new Sample("d9", 100, 40));

        PChartResponse chart = SpcCalculator.pChart(samples);

        assertThat(chart.points().get(9).violations()).containsExactly(SpcCalculator.BEYOND_UCL);
        assertThat(chart.points().get(8).violations()).isEmpty();
    }

    @Test
    void runRuleFlagsEighthConsecutivePointAboveCenter() {
        List<Sample> samples = new ArrayList<>();
        for (int day = 0; day < 8; day++) {
            samples.add(new Sample("low" + day, 1000, 80));
        }
        for (int day = 0; day < 8; day++) {
            samples.add(new Sample("high" + day, 1000, 120));
        }

        List<PChartResponse.Point> points = SpcCalculator.pChart(samples).points();

        assertThat(points.get(14).violations()).doesNotContain(SpcCalculator.RUN_ABOVE_CENTER);
        assertThat(points.get(15).violations()).contains(SpcCalculator.RUN_ABOVE_CENTER);
    }

    @Test
    void chartHandlesNoDefectsAndNoData() {
        assertThat(SpcCalculator.pChart(List.of()).points()).isEmpty();
        PChartResponse.Point point = SpcCalculator.pChart(List.of(new Sample("d", 10, 0))).points().get(0);
        assertThat(point.ucl()).isZero();
        assertThat(point.violations()).isEmpty();
    }

    @Test
    void equipmentWithDefectSpikeIsSuspectAndRankedFirst() {
        List<EquipmentCommonalityResponse> result = SpcCalculator.commonality(List.of(
                new EquipmentCount("ETCH", "ETCH-01", 200, 10),
                new EquipmentCount("ETCH", "ETCH-02", 200, 12),
                new EquipmentCount("ETCH", "ETCH-03", 200, 90)), RULE);

        assertThat(result.get(0).equipmentCode()).isEqualTo("ETCH-03");
        assertThat(result.get(0).suspect()).isTrue();
        assertThat(result.get(0).otherDefectRate()).isCloseTo(22.0 / 400, within(1e-9));
        assertThat(result).filteredOn(EquipmentCommonalityResponse::suspect).hasSize(1);
    }

    @Test
    void smallSampleAndSingleEquipmentStepAreNotSuspect() {
        List<EquipmentCommonalityResponse> result = SpcCalculator.commonality(List.of(
                new EquipmentCount("ETCH", "ETCH-01", 10, 9),
                new EquipmentCount("ETCH", "ETCH-02", 500, 5),
                new EquipmentCount("CMP", "CMP-01", 300, 200)), RULE);

        assertThat(result).noneMatch(EquipmentCommonalityResponse::suspect);
    }

    @Test
    void equipmentThatLosesSignalAfterExclusionIsMarkedConfounded() {
        List<EquipmentCommonalityResponse> initial = SpcCalculator.commonality(List.of(
                new EquipmentCount("ETCH", "ETCH-01", 200, 10),
                new EquipmentCount("ETCH", "ETCH-03", 200, 90),
                new EquipmentCount("CMP", "CMP-01", 200, 20),
                new EquipmentCount("CMP", "CMP-02", 200, 80)), RULE);

        List<EquipmentCommonalityResponse> result = SpcCalculator.resolveConfounding(initial, List.of("ETCH-03"));

        assertThat(result.get(0).equipmentCode()).isEqualTo("ETCH-03");
        assertThat(result).filteredOn(EquipmentCommonalityResponse::suspect).hasSize(1);
        assertThat(result).filteredOn(row -> "CMP-02".equals(row.equipmentCode())).singleElement()
                .satisfies(row -> assertThat(row.confoundedWith()).isEqualTo("ETCH-03"));
    }

    @Test
    void cleanComparisonGroupHasNoLiftButCanBeSuspect() {
        EquipmentCommonalityResponse first = SpcCalculator.commonality(List.of(
                new EquipmentCount("ETCH", "ETCH-01", 100, 30),
                new EquipmentCount("ETCH", "ETCH-02", 100, 0)), RULE).get(0);

        assertThat(first.equipmentCode()).isEqualTo("ETCH-01");
        assertThat(first.lift()).isNull();
        assertThat(first.suspect()).isTrue();
    }
}
