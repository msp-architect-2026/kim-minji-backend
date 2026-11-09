package com.minjikim.codecobainbackend;

import com.minjikim.codecobainbackend.dto.request.LotCreateRequest;
import com.minjikim.codecobainbackend.dto.response.AiPredictionResponse;
import com.minjikim.codecobainbackend.dto.response.FailureItemResponse;
import com.minjikim.codecobainbackend.dto.response.FailureSummaryResponse;
import com.minjikim.codecobainbackend.dto.response.InspectionResponse;
import com.minjikim.codecobainbackend.entity.InspectionStatus;
import com.minjikim.codecobainbackend.entity.WaferStatus;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.service.*;
import com.minjikim.codecobainbackend.util.BusinessContext;
import com.minjikim.codecobainbackend.util.MinioUploader;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class FailureAnalysisIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static MockMultipartFile image;

    @Autowired private LotService lotService;
    @Autowired private InspectionService inspectionService;
    @Autowired private FailureAnalysisService failures;
    @Autowired private InspectionReaper reaper;
    @Autowired private MesMetrics metrics;
    @Autowired private LotDispositionService disposition;
    @Autowired private MeterRegistry registry;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private MinioUploader uploader;
    @MockBean private AiAnalysisService ai;

    @BeforeAll
    static void createImage() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", bytes);
        image = new MockMultipartFile("file", "w.png", "image/png", bytes.toByteArray());
    }

    @BeforeEach
    void stubUpload() {
        when(uploader.upload(any(), anyString())).thenReturn("key");
    }

    @AfterEach
    void clearContext() {
        BusinessContext.clear();
    }

    @Test
    void businessKeysAreBoundDuringProcessing() {
        Long lotId = createLot(1);
        AtomicReference<String> seenLot = new AtomicReference<>();
        AtomicReference<String> seenInspection = new AtomicReference<>();
        when(ai.analyze("key")).thenAnswer(call -> {
            seenLot.set(MDC.get(BusinessContext.LOT_KEY));
            seenInspection.set(MDC.get(BusinessContext.INSPECTION_KEY));
            return new AiPredictionResponse(null, "none", 0.95);
        });

        InspectionResponse result = inspectionService.inspectWafer(lotId, 1, image, null);

        assertThat(seenLot).hasValue(lotId.toString());
        assertThat(seenInspection).hasValue(result.id().toString());
    }

    @Test
    void failuresAreSummarizedAndRetriedOnesAreResolved() {
        Long lotId = createLot(3);
        long before = failures.summary(1).unresolved();
        double counterBefore = registry.counter("mes.inspection.failures", "error_code", "AI_TIMEOUT").count();
        when(ai.analyze("key")).thenThrow(new WaferException(ErrorCode.AI_TIMEOUT));
        inspectFails(lotId, 1);
        inspectFails(lotId, 2);

        FailureSummaryResponse summary = failures.summary(1);
        assertThat(summary.unresolved()).isEqualTo(before + 2);
        assertThat(summary.rows()).anySatisfy(row -> {
            assertThat(row.stage()).isEqualTo("INFERENCE");
            assertThat(row.errorCode()).isEqualTo("AI_TIMEOUT");
        });
        assertThat(registry.counter("mes.inspection.failures", "error_code", "AI_TIMEOUT").count()).isEqualTo(counterBefore + 2);

        FailureItemResponse item = failures.list(1, "AI_TIMEOUT", 0, 1).getContent().get(0);
        assertThat(item.lotId()).isEqualTo(lotId);
        assertThat(item.traceId()).isNotBlank();
        assertThat(item.retryable()).isTrue();

        doReturn(new AiPredictionResponse(null, "none", 0.95)).when(ai).analyze("key");
        inspectionService.retry(item.inspectionId());

        assertThat(failures.summary(1).unresolved()).isEqualTo(before + 1);
    }

    @Test
    void uploadFailureIsReportedAsUploadStage() {
        Long lotId = createLot(1);
        when(uploader.upload(any(), anyString())).thenThrow(new WaferException(ErrorCode.STORAGE_UPLOAD_FAILED));
        inspectFails(lotId, 1);

        FailureItemResponse item = failures.list(1, "STORAGE_UPLOAD_FAILED", 0, 1).getContent().get(0);

        assertThat(item.stage()).isEqualTo("UPLOAD");
        assertThat(item.retryable()).isFalse();
    }

    @Test
    void reaperFailsOnlyInspectionsPastTimeout() {
        Long lotId = createLot(2);
        Long stuck = insertActiveInspection(lotId, 1, 20);
        Long fresh = insertActiveInspection(lotId, 2, 0);

        reaper.reap();

        assertThat(status(stuck)).isEqualTo("FAILED");
        assertThat(status(fresh)).isEqualTo("INFERRING");
        assertThat(lotService.detail(lotId).wafers().get(0).status()).isEqualTo(WaferStatus.FAILED);
        assertThat(inspectionService.findEvents(stuck)).last().satisfies(event -> {
            assertThat(event.toStatus()).isEqualTo(InspectionStatus.FAILED);
            assertThat(event.errorCode()).isEqualTo("INSPECTION_STUCK");
        });
        doReturn(new AiPredictionResponse(null, "none", 0.95)).when(ai).analyze("key");
        assertThat(inspectionService.retry(stuck).status()).isEqualTo(InspectionStatus.DONE);
    }

    @Test
    void businessGaugesReflectDatabase() {
        Long lotId = createLot(1);
        disposition.holdManually(lotId, "점검", "kim");

        metrics.refresh();

        assertThat(registry.get("mes.lots.on_hold").gauge().value()).isGreaterThanOrEqualTo(1);
        assertThat(registry.get("mes.reviews.pending").gauge()).isNotNull();
        assertThat(registry.counter("mes.lot.holds", "source", "MANUAL").count()).isGreaterThanOrEqualTo(1);
    }

    private void inspectFails(Long lotId, int slot) {
        assertThatThrownBy(() -> inspectionService.inspectWafer(lotId, slot, image, null)).isInstanceOf(WaferException.class);
    }

    private Long insertActiveInspection(Long lotId, int slot, int minutesAgo) {
        Long waferId = jdbc.queryForObject("SELECT id FROM wafer WHERE lot_id = ? AND slot_no = ?", Long.class, lotId, slot);
        LocalDateTime updatedAt = LocalDateTime.now().minusMinutes(minutesAgo);
        jdbc.update("UPDATE wafer SET status = 'INSPECTING' WHERE id = ?", waferId);
        jdbc.update("""
                INSERT INTO inspection (wafer_id, filename, image_key, status, attempt, version, created_at, updated_at)
                VALUES (?, 'f', 'key', 'INFERRING', 1, 0, ?, ?)""",
                waferId, updatedAt, updatedAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM inspection WHERE wafer_id = ?", Long.class, waferId);
    }

    private String status(Long id) {
        return jdbc.queryForObject("SELECT status FROM inspection WHERE id = ?", String.class, id);
    }

    private Long createLot(int waferCount) {
        return lotService.create(new LotCreateRequest("F-" + UUID.randomUUID().toString().substring(0, 8), "P", waferCount)).id();
    }
}
