package com.minjikim.codecobainbackend;

import com.minjikim.codecobainbackend.dto.request.LotCreateRequest;
import com.minjikim.codecobainbackend.dto.request.ReviewRequest;
import com.minjikim.codecobainbackend.dto.request.SimulationRequest;
import com.minjikim.codecobainbackend.dto.response.*;
import com.minjikim.codecobainbackend.entity.HoldSource;
import com.minjikim.codecobainbackend.entity.InspectionStatus;
import com.minjikim.codecobainbackend.entity.LotStatus;
import com.minjikim.codecobainbackend.entity.WaferStatus;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.service.*;
import com.minjikim.codecobainbackend.util.MinioUploader;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class HoldAndReviewIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static MockMultipartFile image;

    @Autowired private LotService lotService;
    @Autowired private InspectionService inspectionService;
    @Autowired private InspectionReviewService reviewService;
    @Autowired private LotDispositionService disposition;
    @Autowired private SimulationService simulationService;
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

    @Test
    void reviewStoresBothJudgementsAndClosesLot() {
        Long lotId = createLot(1);
        aiReturns("Scratch", 0.5);
        InspectionResponse pending = inspectionService.inspectWafer(lotId, 1, image, null);

        InspectionResponse reviewed = reviewService.review(pending.id(), new ReviewRequest("none", "kim", "오탐"));

        assertThat(reviewed.status()).isEqualTo(InspectionStatus.DONE);
        assertThat(reviewed.prediction()).isEqualTo("Scratch");
        assertThat(reviewed.finalPrediction()).isEqualTo("none");
        LotDetailResponse lot = lotService.detail(lotId);
        assertThat(lot.status()).isEqualTo(LotStatus.COMPLETED);
        assertThat(lot.wafers().get(0).status()).isEqualTo(WaferStatus.PASS);
        assertThat(jdbc.queryForObject("SELECT decision FROM inspection_review WHERE inspection_id = ?", String.class, pending.id()))
                .isEqualTo("RECLASSIFIED");
        assertThat(inspectionService.findEvents(pending.id())).last()
                .satisfies(e -> {
                    assertThat(e.fromStatus()).isEqualTo(InspectionStatus.NEED_REVIEW);
                    assertThat(e.toStatus()).isEqualTo(InspectionStatus.DONE);
                });
    }

    @Test
    void inspectionCanBeReviewedOnlyOnce() {
        Long lotId = createLot(2);
        aiReturns("Scratch", 0.5);
        InspectionResponse pending = inspectionService.inspectWafer(lotId, 1, image, null);
        ReviewRequest request = new ReviewRequest("Scratch", "kim", null);
        reviewService.review(pending.id(), request);

        assertFailsWith(ErrorCode.INSPECTION_NOT_REVIEWABLE, () -> reviewService.review(pending.id(), request));
    }

    @Test
    void invalidReviewIsRejectedAndPendingQueueListsInspection() {
        Long lotId = createLot(2);
        aiReturns("Scratch", 0.5);
        InspectionResponse pending = inspectionService.inspectWafer(lotId, 1, image, null);

        assertFailsWith(ErrorCode.INVALID_REQUEST, () -> reviewService.review(pending.id(), new ReviewRequest("Unknown", "kim", null)));
        assertFailsWith(ErrorCode.INVALID_REQUEST, () -> reviewService.review(pending.id(), new ReviewRequest("none", " ", null)));
        assertThat(reviewService.pending(0, 100).getContent())
                .anySatisfy(row -> {
                    assertThat(row.inspectionId()).isEqualTo(pending.id());
                    assertThat(row.lotId()).isEqualTo(lotId);
                    assertThat(row.slotNo()).isEqualTo(1);
                });
    }

    @Test
    void waferWaitingForReviewCannotBeReinspected() {
        Long lotId = createLot(2);
        aiReturns("Scratch", 0.5);
        inspectionService.inspectWafer(lotId, 1, image, null);

        assertFailsWith(ErrorCode.INSPECTION_NEEDS_REVIEW, () -> inspectionService.inspectWafer(lotId, 1, image, null));
    }

    @Test
    void defectRateTriggersAutoHoldAndBlocksInspection() {
        Long lotId = createLot(6);
        aiReturns("Scratch", 0.95);

        for (int slot = 1; slot <= 4; slot++) {
            inspectionService.inspectWafer(lotId, slot, image, null);
            assertThat(lotService.detail(lotId).status()).isEqualTo(LotStatus.IN_PROGRESS);
        }
        inspectionService.inspectWafer(lotId, 5, image, null);

        LotDetailResponse lot = lotService.detail(lotId);
        assertThat(lot.status()).isEqualTo(LotStatus.HOLD);
        assertThat(lot.holds()).singleElement().satisfies(hold -> {
            assertThat(hold.source()).isEqualTo(HoldSource.AUTO);
            assertThat(hold.reason()).contains("5/5");
            assertThat(hold.releasedAt()).isNull();
        });
        assertFailsWith(ErrorCode.LOT_NOT_ACTIVE, () -> inspectionService.inspectWafer(lotId, 6, image, null));
    }

    @Test
    void releasedLotIsNotAutoHeldAgainAndCanComplete() {
        Long lotId = createLot(6);
        aiReturns("Scratch", 0.95);
        for (int slot = 1; slot <= 5; slot++) {
            inspectionService.inspectWafer(lotId, slot, image, null);
        }

        disposition.release(lotId, "장비 점검 완료", "kim");
        assertThat(lotService.detail(lotId).status()).isEqualTo(LotStatus.IN_PROGRESS);
        inspectionService.inspectWafer(lotId, 6, image, null);

        LotDetailResponse lot = lotService.detail(lotId);
        assertThat(lot.status()).isEqualTo(LotStatus.COMPLETED);
        assertThat(lot.holds()).singleElement().satisfies(hold -> {
            assertThat(hold.releasedBy()).isEqualTo("kim");
            assertThat(hold.releasedAt()).isNotNull();
        });
    }

    @Test
    void lowDefectRateDoesNotHold() {
        Long lotId = createLot(6);
        aiReturns("none", 0.95);
        for (int slot = 1; slot <= 5; slot++) {
            inspectionService.inspectWafer(lotId, slot, image, null);
        }
        aiReturns("Scratch", 0.95);
        inspectionService.inspectWafer(lotId, 6, image, null);

        assertThat(lotService.detail(lotId).status()).isEqualTo(LotStatus.COMPLETED);
    }

    @Test
    void manualHoldBlocksRetryAndRequiresValidTransitions() {
        Long lotId = createLot(2);
        when(ai.analyze("key")).thenThrow(new WaferException(ErrorCode.AI_TIMEOUT));
        assertFailsWith(ErrorCode.AI_TIMEOUT, () -> inspectionService.inspectWafer(lotId, 1, image, null));
        Long failedId = lotService.detail(lotId).wafers().get(0).latestInspection().id();

        assertFailsWith(ErrorCode.LOT_NOT_ON_HOLD, () -> disposition.release(lotId, "c", "kim"));
        disposition.holdManually(lotId, "설비 점검", "kim");

        assertFailsWith(ErrorCode.LOT_NOT_ACTIVE, () -> disposition.holdManually(lotId, "중복", "kim"));
        assertFailsWith(ErrorCode.LOT_NOT_ACTIVE, () -> inspectionService.retry(failedId));
        assertFailsWith(ErrorCode.INVALID_REQUEST, () -> disposition.release(lotId, " ", "kim"));
        assertThat(lotService.detail(lotId).holds()).singleElement()
                .satisfies(hold -> assertThat(hold.source()).isEqualTo(HoldSource.MANUAL));
    }

    @Test
    void reviewIsAllowedWhileLotIsOnHold() {
        Long lotId = createLot(2);
        aiReturns("Scratch", 0.5);
        InspectionResponse pending = inspectionService.inspectWafer(lotId, 1, image, null);
        disposition.holdManually(lotId, "점검", "kim");

        InspectionResponse reviewed = reviewService.review(pending.id(), new ReviewRequest("Scratch", "kim", null));

        assertThat(reviewed.status()).isEqualTo(InspectionStatus.DONE);
        assertThat(lotService.detail(lotId).status()).isEqualTo(LotStatus.HOLD);
    }

    @Test
    void concurrentHoldsCreateOneHoldRecord() {
        Long lotId = createLot(1);

        CompletableFuture<?>[] calls = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        disposition.holdManually(lotId, "동시 요청", "kim");
                    } catch (WaferException expectedForLosers) {
                        assertThat(expectedForLosers.getErrorCode()).isEqualTo(ErrorCode.LOT_NOT_ACTIVE);
                    }
                }))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(calls).join();

        assertThat(lotService.detail(lotId).holds()).hasSize(1);
    }

    @Test
    void simulationHoldsExcursionLots() {
        simulationService.simulate(new SimulationRequest(20, 3L, "ETCH-03", "Edge-Ring"));

        Integer held = jdbc.queryForObject("""
                SELECT COUNT(*) FROM lot l JOIN lot_hold h ON h.lot_id = l.id
                WHERE l.lot_no LIKE 'SIM-%' AND l.status = 'HOLD' AND h.source = 'AUTO'""", Integer.class);
        assertThat(held).isPositive();
    }

    private Long createLot(int waferCount) {
        return lotService.create(new LotCreateRequest("L-" + UUID.randomUUID().toString().substring(0, 8), "P", waferCount)).id();
    }

    private void aiReturns(String prediction, double confidence) {
        org.mockito.Mockito.doReturn(new AiPredictionResponse(null, prediction, confidence)).when(ai).analyze("key");
    }

    private void assertFailsWith(ErrorCode code, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(WaferException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
