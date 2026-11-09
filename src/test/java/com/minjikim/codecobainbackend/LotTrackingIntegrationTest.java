package com.minjikim.codecobainbackend;

import com.minjikim.codecobainbackend.dto.request.LotCreateRequest;
import com.minjikim.codecobainbackend.dto.request.TrackInRequest;
import com.minjikim.codecobainbackend.dto.request.TrackOutRequest;
import com.minjikim.codecobainbackend.dto.response.LotDetailResponse;
import com.minjikim.codecobainbackend.dto.response.LotRouteResponse;
import com.minjikim.codecobainbackend.dto.response.LotRouteResponse.StepStatus;
import com.minjikim.codecobainbackend.dto.response.ProcessHistoryResponse;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.service.LotDispositionService;
import com.minjikim.codecobainbackend.service.LotService;
import com.minjikim.codecobainbackend.service.LotTrackingService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class LotTrackingIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired private LotService lotService;
    @Autowired private LotTrackingService tracking;
    @Autowired private LotDispositionService disposition;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void trackInAndOutRecordHistoryForEveryWafer() {
        LotDetailResponse lot = createLot(3);

        LotRouteResponse in = tracking.trackIn(lot.id(), new TrackInRequest("PHOTO", "PHOTO-01", "R1"));
        assertThat(in.currentStep()).isEqualTo("PHOTO");
        assertThat(in.steps().get(0).status()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(historyRows(lot.id())).isEqualTo(3);

        LotRouteResponse out = tracking.trackOut(lot.id(), new TrackOutRequest("PHOTO"));
        assertThat(out.currentStep()).isNull();
        assertThat(out.nextStep()).isEqualTo("ETCH");
        assertThat(out.availableEquipment()).containsExactly("ETCH-01", "ETCH-02", "ETCH-03");
        assertThat(out.steps().get(0).status()).isEqualTo(StepStatus.DONE);

        List<ProcessHistoryResponse> history = lotService.processHistory(lot.wafers().get(2).waferId());
        assertThat(history).singleElement().satisfies(row -> {
            assertThat(row.equipmentCode()).isEqualTo("PHOTO-01");
            assertThat(row.recipe()).isEqualTo("R1");
            assertThat(row.endedAt()).isNotNull();
        });
    }

    @Test
    void routeOrderIsEnforced() {
        Long id = createLot(1).id();

        assertFailsWith(ErrorCode.ROUTE_VIOLATION, () -> tracking.trackIn(id, new TrackInRequest("ETCH", "ETCH-01", "R1")));
    }

    @Test
    void equipmentMustBelongToStepAndExist() {
        Long id = createLot(1).id();

        assertFailsWith(ErrorCode.INVALID_REQUEST, () -> tracking.trackIn(id, new TrackInRequest("PHOTO", "ETCH-01", "R1")));
        assertFailsWith(ErrorCode.EQUIPMENT_NOT_FOUND, () -> tracking.trackIn(id, new TrackInRequest("PHOTO", "NOPE", "R1")));
        assertFailsWith(ErrorCode.STEP_NOT_FOUND, () -> tracking.trackIn(id, new TrackInRequest("NOPE", "ETCH-01", "R1")));
        assertFailsWith(ErrorCode.INVALID_REQUEST, () -> tracking.trackIn(id, new TrackInRequest("PHOTO", "PHOTO-01", " ")));
        assertFailsWith(ErrorCode.LOT_NOT_FOUND, () -> tracking.trackIn(-1L, new TrackInRequest("PHOTO", "PHOTO-01", "R1")));
    }

    @Test
    void retransmittedEventsAreIdempotent() {
        Long id = createLot(2).id();
        TrackInRequest in = new TrackInRequest("PHOTO", "PHOTO-01", "R1");
        tracking.trackIn(id, in);
        tracking.trackIn(id, in);
        assertThat(historyRows(id)).isEqualTo(2);

        tracking.trackOut(id, new TrackOutRequest("PHOTO"));
        LotRouteResponse again = tracking.trackOut(id, new TrackOutRequest("PHOTO"));
        assertThat(again.nextStep()).isEqualTo("ETCH");
    }

    @Test
    void conflictingEventsAreRejected() {
        Long id = createLot(1).id();
        assertFailsWith(ErrorCode.LOT_NOT_TRACKED_IN, () -> tracking.trackOut(id, new TrackOutRequest("PHOTO")));

        tracking.trackIn(id, new TrackInRequest("PHOTO", "PHOTO-01", "R1"));

        assertFailsWith(ErrorCode.LOT_ALREADY_TRACKED_IN, () -> tracking.trackIn(id, new TrackInRequest("PHOTO", "PHOTO-02", "R1")));
        assertFailsWith(ErrorCode.LOT_NOT_TRACKED_IN, () -> tracking.trackOut(id, new TrackOutRequest("ETCH")));
    }

    @Test
    void holdBlocksTrackInButNotTrackOut() {
        Long id = createLot(1).id();
        tracking.trackIn(id, new TrackInRequest("PHOTO", "PHOTO-01", "R1"));
        disposition.holdManually(id, "점검", "kim");

        assertThat(tracking.trackOut(id, new TrackOutRequest("PHOTO")).nextStep()).isEqualTo("ETCH");
        assertFailsWith(ErrorCode.LOT_NOT_ACTIVE, () -> tracking.trackIn(id, new TrackInRequest("ETCH", "ETCH-01", "R1")));
    }

    @Test
    void finishedRouteRejectsFurtherTrackIn() {
        Long id = createLot(1).id();
        for (String[] step : new String[][]{{"PHOTO", "PHOTO-01"}, {"ETCH", "ETCH-01"}, {"DEPO", "DEPO-01"}, {"CMP", "CMP-01"}}) {
            tracking.trackIn(id, new TrackInRequest(step[0], step[1], "R1"));
            tracking.trackOut(id, new TrackOutRequest(step[0]));
        }

        LotRouteResponse route = tracking.route(id);

        assertThat(route.nextStep()).isNull();
        assertThat(route.steps()).allMatch(step -> step.status() == StepStatus.DONE);
        assertFailsWith(ErrorCode.ROUTE_VIOLATION, () -> tracking.trackIn(id, new TrackInRequest("PHOTO", "PHOTO-01", "R1")));
    }

    @Test
    void concurrentTrackInsCreateOneHistorySet() {
        Long id = createLot(5).id();

        CompletableFuture.allOf(IntStream.range(0, 8)
                .mapToObj(i -> CompletableFuture.runAsync(() -> tracking.trackIn(id, new TrackInRequest("PHOTO", "PHOTO-01", "R1"))))
                .toArray(CompletableFuture[]::new)).join();

        assertThat(historyRows(id)).isEqualTo(5);
    }

    private int historyRows(Long lotId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM wafer_process_history h JOIN wafer w ON w.id = h.wafer_id WHERE w.lot_id = ?""", Integer.class, lotId);
    }

    private LotDetailResponse createLot(int waferCount) {
        return lotService.create(new LotCreateRequest("T-" + UUID.randomUUID().toString().substring(0, 8), "P", waferCount));
    }

    private void assertFailsWith(ErrorCode code, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(WaferException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
