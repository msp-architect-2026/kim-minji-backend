package com.minjikim.codecobainbackend.service;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LotDispositionRuleTest {

    private static final double BASELINE = 0.08;
    private static final double SIGNIFICANCE = 0.001;

    @Test
    void binomialTailMatchesKnownValues() {
        assertThat(LotDispositionService.binomialTail(5, 0, BASELINE)).isCloseTo(1.0, within(1e-12));
        assertThat(LotDispositionService.binomialTail(5, 5, BASELINE)).isCloseTo(Math.pow(BASELINE, 5), within(1e-12));
        assertThat(LotDispositionService.binomialTail(5, 2, BASELINE)).isCloseTo(0.0544, within(1e-4));
    }

    @Test
    void smallSampleNoiseIsNotHeld() {
        assertThat(LotDispositionService.binomialTail(5, 2, BASELINE)).isGreaterThan(SIGNIFICANCE);
        assertThat(LotDispositionService.binomialTail(6, 2, BASELINE)).isGreaterThan(SIGNIFICANCE);
    }

    // 정상 Lot(결함 8%)을 25장 순서대로 판정할 때 한 번이라도 Hold될 확률과, 이상 Lot(결함 60%)을 잡아내는지 비교한다
    @Test
    void falseHoldRateDropsWhileExcursionIsStillCaught() {
        assertThat(holdRate(0.08, 50_000)).isLessThan(0.01);
        assertThat(holdRate(0.60, 5_000)).isGreaterThan(0.99);
    }

    private double holdRate(double trueRate, int lots) {
        Random random = new Random(1);
        int held = 0;
        for (int lot = 0; lot < lots; lot++) {
            int defects = 0;
            for (int judged = 1; judged <= 25; judged++) {
                if (random.nextDouble() < trueRate) {
                    defects++;
                }
                if (judged >= 5 && LotDispositionService.binomialTail(judged, defects, BASELINE) < SIGNIFICANCE) {
                    held++;
                    break;
                }
            }
        }
        return (double) held / lots;
    }
}
