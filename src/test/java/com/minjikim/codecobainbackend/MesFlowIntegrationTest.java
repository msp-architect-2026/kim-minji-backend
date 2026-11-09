package com.minjikim.codecobainbackend;

import com.minjikim.codecobainbackend.dto.request.LotCreateRequest;
import com.minjikim.codecobainbackend.dto.request.SimulationRequest;
import com.minjikim.codecobainbackend.dto.response.*;
import com.minjikim.codecobainbackend.entity.InspectionStatus;
import com.minjikim.codecobainbackend.entity.LotStatus;
import com.minjikim.codecobainbackend.entity.WaferStatus;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.service.AiAnalysisService;
import com.minjikim.codecobainbackend.service.InspectionService;
import com.minjikim.codecobainbackend.service.LotService;
import com.minjikim.codecobainbackend.service.SimulationService;
import com.minjikim.codecobainbackend.util.MinioUploader;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// 실제 MySQL로 Lot·검사 상태 머신을 검증한다. MinIO와 AI 호출만 대체한다
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class MesFlowIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static MockMultipartFile image;

    @Autowired private LotService lotService;
    @Autowired private InspectionService inspectionService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SimulationService simulationService;
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
    void successfulInspectionRecordsEveryTransitionAndCompletesLot() {
        Long lotId = createLot(1);
        aiReturns("none", 0.95);

        InspectionResponse result = inspectionService.inspectWafer(lotId, 1, image, null);

        assertThat(result.status()).isEqualTo(InspectionStatus.DONE);
        assertThat(result.modelVersion()).isNotBlank();
        assertThat(statuses(result.id())).containsExactly(InspectionStatus.RECEIVED, InspectionStatus.UPLOADED,
                InspectionStatus.INFERRING, InspectionStatus.DONE);
        LotDetailResponse lot = lotService.detail(lotId);
        assertThat(lot.status()).isEqualTo(LotStatus.COMPLETED);
        assertThat(lot.wafers().get(0).status()).isEqualTo(WaferStatus.PASS);
    }

    @Test
    void completedLotRejectsNewInspection() {
        Long lotId = createLot(1);
        aiReturns("Scratch", 0.95);
        inspectionService.inspectWafer(lotId, 1, image, null);

        assertFailsWith(ErrorCode.LOT_NOT_ACTIVE, () -> inspectionService.inspectWafer(lotId, 1, image, null));
    }

    @Test
    void lowConfidenceNeedsReviewAndKeepsLotOpen() {
        Long lotId = createLot(1);
        aiReturns("Scratch", 0.5);

        InspectionResponse result = inspectionService.inspectWafer(lotId, 1, image, null);

        assertThat(result.status()).isEqualTo(InspectionStatus.NEED_REVIEW);
        LotDetailResponse lot = lotService.detail(lotId);
        assertThat(lot.status()).isEqualTo(LotStatus.IN_PROGRESS);
        assertThat(lot.wafers().get(0).status()).isEqualTo(WaferStatus.REVIEW);
    }

    @Test
    void aiFailureIsRecordedAndCanBeRetriedWithoutReupload() {
        Long lotId = createLot(2);
        when(ai.analyze("key")).thenThrow(new WaferException(ErrorCode.AI_TIMEOUT));

        assertFailsWith(ErrorCode.AI_TIMEOUT, () -> inspectionService.inspectWafer(lotId, 1, image, null));

        InspectionResponse failed = lotService.detail(lotId).wafers().get(0).latestInspection();
        assertThat(failed.status()).isEqualTo(InspectionStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("AI_TIMEOUT");
        assertThat(failed.traceId()).isNotBlank();
        assertThat(failed.retryable()).isTrue();

        doReturn(new AiPredictionResponse(null, "Donut", 0.9)).when(ai).analyze("key");
        InspectionResponse retried = inspectionService.retry(failed.id());

        assertThat(retried.status()).isEqualTo(InspectionStatus.DONE);
        assertThat(retried.attempt()).isEqualTo(2);
        assertThat(retried.errorCode()).isNull();
        assertThat(statuses(failed.id())).containsExactly(InspectionStatus.RECEIVED, InspectionStatus.UPLOADED,
                InspectionStatus.INFERRING, InspectionStatus.FAILED, InspectionStatus.INFERRING, InspectionStatus.DONE);
        verify(uploader, times(1)).upload(any(), anyString());
    }

    @Test
    void uploadFailureIsRecordedButNotRetryable() {
        Long lotId = createLot(1);
        when(uploader.upload(any(), anyString())).thenThrow(new WaferException(ErrorCode.STORAGE_UPLOAD_FAILED));

        assertFailsWith(ErrorCode.STORAGE_UPLOAD_FAILED, () -> inspectionService.inspectWafer(lotId, 1, image, null));

        InspectionResponse failed = lotService.detail(lotId).wafers().get(0).latestInspection();
        assertThat(failed.retryable()).isFalse();
        assertFailsWith(ErrorCode.INSPECTION_NOT_RETRYABLE, () -> inspectionService.retry(failed.id()));
    }

    @Test
    void sameIdempotencyKeyReturnsExistingInspection() {
        Long lotId = createLot(2);
        aiReturns("none", 0.95);
        String key = UUID.randomUUID().toString();

        InspectionResponse first = inspectionService.inspectWafer(lotId, 1, image, key);
        InspectionResponse replay = inspectionService.inspectWafer(lotId, 1, image, key);

        assertThat(replay.id()).isEqualTo(first.id());
        verify(uploader, times(1)).upload(any(), anyString());
    }

    @Test
    void invalidFileLeavesNoInspection() {
        Long lotId = createLot(1);
        MockMultipartFile bad = new MockMultipartFile("file", "x.png", "image/png", "nope".getBytes());

        assertFailsWith(ErrorCode.INVALID_FILE, () -> inspectionService.inspectWafer(lotId, 1, bad, null));

        assertThat(lotService.detail(lotId).wafers().get(0).latestInspection()).isNull();
    }

    @Test
    void duplicateLotAndMissingWaferAreRejected() {
        Long lotId = createLot(1);
        String lotNo = lotService.detail(lotId).lotNo();

        assertFailsWith(ErrorCode.LOT_ALREADY_EXISTS, () -> lotService.create(new LotCreateRequest(lotNo, "P", 1)));
        assertFailsWith(ErrorCode.WAFER_NOT_FOUND, () -> inspectionService.inspectWafer(lotId, 9, image, null));
        assertFailsWith(ErrorCode.INVALID_REQUEST, () -> lotService.create(new LotCreateRequest("X", "P", 26)));
    }

    @Test
    void uploadWithoutLotStillWorks() {
        aiReturns("Center", 0.9);

        AiPredictionResponse result = inspectionService.inspect(image);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getPrediction()).isEqualTo("Center");
    }

    @Test
    void simulationConcentratesDefectOnExcursionEquipment() {
        SimulationResponse response = simulationService.simulate(new SimulationRequest(30, 7L, "ETCH-03", "Edge-Ring"));

        assertThat(response.wafers()).isEqualTo(750);
        assertThat(response.excursionLots()).isPositive().isLessThan(30);
        double onEquipment = edgeRingRate("=");
        double elsewhere = edgeRingRate("<>");
        assertThat(onEquipment).isGreaterThan(0.4);
        assertThat(elsewhere).isLessThan(0.05);
    }

    private double edgeRingRate(String equipmentOperator) {
        return jdbc.queryForObject("""
                SELECT AVG(i.prediction = 'Edge-Ring')
                FROM inspection i
                JOIN wafer_process_history h ON h.wafer_id = i.wafer_id
                JOIN process_step s ON s.id = h.step_id AND s.code = 'ETCH'
                JOIN equipment e ON e.id = h.equipment_id
                WHERE i.filename LIKE 'simulated-%' AND e.code """ + " " + equipmentOperator + " 'ETCH-03'", Double.class);
    }

    private Long createLot(int waferCount) {
        return lotService.create(new LotCreateRequest("L-" + UUID.randomUUID().toString().substring(0, 8), "P", waferCount)).id();
    }

    private void aiReturns(String prediction, double confidence) {
        when(ai.analyze("key")).thenReturn(new AiPredictionResponse(null, prediction, confidence));
    }

    private List<InspectionStatus> statuses(Long inspectionId) {
        return inspectionService.findEvents(inspectionId).stream().map(InspectionEventResponse::toStatus).toList();
    }

    private void assertFailsWith(ErrorCode code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(WaferException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
