package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.entity.InspectionStatus;
import com.minjikim.codecobainbackend.repository.InspectionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// 서버가 처리 도중 죽으면 검사가 진행 상태에 남아 슬롯이 묶인다. 일정 시간 변화가 없는 검사를 FAILED로 돌려 재검사할 수 있게 한다
@Component
@RequiredArgsConstructor
public class InspectionReaper {

    private static final Logger log = LoggerFactory.getLogger(InspectionReaper.class);
    private static final List<InspectionStatus> ACTIVE = List.of(InspectionStatus.RECEIVED, InspectionStatus.UPLOADED, InspectionStatus.INFERRING);
    private static final int BATCH_SIZE = 100;

    private final InspectionRepository inspections;
    private final InspectionLifecycle lifecycle;
    private final MeterRegistry meterRegistry;

    @Value("${inspection.stuck.timeout}")
    private Duration timeout;

    @Scheduled(fixedDelayString = "${inspection.stuck.scan-interval}", initialDelayString = "${inspection.stuck.scan-interval}")
    public void reap() {
        LocalDateTime cutoff = LocalDateTime.now().minus(timeout);
        for (Long id : inspections.findStuckIds(ACTIVE, cutoff, PageRequest.of(0, BATCH_SIZE))) {
            try {
                if (lifecycle.failIfStuck(id, cutoff)) {
                    meterRegistry.counter("mes.inspection.stuck.recovered").increment();
                    log.warn("멈춘 검사를 FAILED로 처리했습니다: inspectionId={}", id);
                }
            } catch (OptimisticLockingFailureException e) {
                // 요청 처리나 다른 인스턴스가 먼저 갱신했다. 다음 주기에 다시 판단한다
                log.debug("멈춘 검사 처리 충돌: inspectionId={}", id);
            }
        }
    }
}
