package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.EquipmentCommonalityResponse;
import com.minjikim.codecobainbackend.entity.InspectionStatus;
import com.minjikim.codecobainbackend.entity.LotStatus;
import com.minjikim.codecobainbackend.repository.InspectionRepository;
import com.minjikim.codecobainbackend.repository.LotRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

// DB 상태에서 나오는 업무 지표를 주기적으로 갱신한다. 스크레이프마다 쿼리가 나가지 않게 값만 노출한다
@Component
public class MesMetrics {

    private static final int SPC_WINDOW_DAYS = 14;

    private final LotRepository lots;
    private final InspectionRepository inspections;
    private final SpcService spcService;
    private final AtomicLong lotsOnHold = new AtomicLong();
    private final AtomicLong reviewsPending = new AtomicLong();
    private final AtomicLong spcOutOfControl = new AtomicLong();
    private final MultiGauge suspectEquipment;

    public MesMetrics(MeterRegistry registry, LotRepository lots, InspectionRepository inspections, SpcService spcService) {
        this.lots = lots;
        this.inspections = inspections;
        this.spcService = spcService;
        Gauge.builder("mes.lots.on_hold", lotsOnHold, AtomicLong::get).register(registry);
        Gauge.builder("mes.reviews.pending", reviewsPending, AtomicLong::get).register(registry);
        Gauge.builder("mes.spc.out_of_control", spcOutOfControl, AtomicLong::get).register(registry);
        suspectEquipment = MultiGauge.builder("mes.equipment.suspect").register(registry);
    }

    @Scheduled(fixedDelayString = "${mes.metrics.refresh-interval}")
    public void refresh() {
        lotsOnHold.set(lots.countByStatus(LotStatus.HOLD));
        reviewsPending.set(inspections.countByStatus(InspectionStatus.NEED_REVIEW));

        String yesterday = LocalDate.now().minusDays(1).toString();
        boolean outOfControl = spcService.pChart(SPC_WINDOW_DAYS, null, null).points().stream()
                .anyMatch(point -> point.date().compareTo(yesterday) >= 0 && !point.violations().isEmpty());
        spcOutOfControl.set(outOfControl ? 1 : 0);

        List<MultiGauge.Row<?>> rows = spcService.commonality(SPC_WINDOW_DAYS, null).stream()
                .filter(EquipmentCommonalityResponse::suspect)
                .<MultiGauge.Row<?>>map(row -> MultiGauge.Row.of(Tags.of("equipment", row.equipmentCode()), 1))
                .toList();
        suspectEquipment.register(rows, true);
    }
}
