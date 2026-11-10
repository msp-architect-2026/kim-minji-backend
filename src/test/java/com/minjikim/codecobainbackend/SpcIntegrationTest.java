package com.minjikim.codecobainbackend;

import com.minjikim.codecobainbackend.dto.request.SimulationRequest;
import com.minjikim.codecobainbackend.dto.response.EquipmentCommonalityResponse;
import com.minjikim.codecobainbackend.dto.response.PChartResponse;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.service.SimulationService;
import com.minjikim.codecobainbackend.service.SpcCalculator;
import com.minjikim.codecobainbackend.service.SpcService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.cache.type=none")
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class SpcIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static final AtomicBoolean seeded = new AtomicBoolean();

    @Autowired private SimulationService simulationService;
    @Autowired private SpcService spcService;
    @Autowired private JdbcTemplate jdbc;
    // 커넥션이 autocommit=false라 트랜잭션 밖 쓰기는 커밋되지 않는다
    @Autowired private TransactionTemplate tx;

    @org.junit.jupiter.api.BeforeEach
    void seedOnce() {
        if (seeded.compareAndSet(false, true)) {
            simulationService.simulate(new SimulationRequest(80, 11L, "ETCH-03", "Edge-Ring", 5));
        }
    }

    @Test
    void commonalityFindsOnlyExcursionEquipment() {
        List<EquipmentCommonalityResponse> result = spcService.commonality(15, "Edge-Ring");

        assertThat(result.get(0).equipmentCode()).isEqualTo("ETCH-03");
        assertThat(result).filteredOn(EquipmentCommonalityResponse::suspect)
                .extracting(EquipmentCommonalityResponse::equipmentCode)
                .containsExactly("ETCH-03");
        assertThat(result).filteredOn(row -> row.confoundedWith() != null)
                .allSatisfy(row -> assertThat(row.confoundedWith()).isEqualTo("ETCH-03"));
    }

    @Test
    void equipmentChartShowsExcursionOnset() {
        PChartResponse chart = spcService.pChart(15, "Edge-Ring", "ETCH-03");

        List<PChartResponse.Point> points = chart.points();
        assertThat(points).isNotEmpty();
        assertThat(points.subList(Math.max(0, points.size() - 5), points.size()))
                .anySatisfy(point -> assertThat(point.violations()).contains(SpcCalculator.BEYOND_UCL));
    }

    @Test
    void reviewedFinalJudgementDrivesSpc() {
        long before = spcService.pChart(15, null, null).points().stream().mapToLong(PChartResponse.Point::defects).sum();
        Long id = jdbc.queryForObject("""
                SELECT MAX(id) FROM inspection WHERE status = 'DONE' AND final_prediction = 'none'""", Long.class);
        tx.executeWithoutResult(status -> jdbc.update("UPDATE inspection SET final_prediction = 'Scratch' WHERE id = ?", id));

        long after = spcService.pChart(15, null, null).points().stream().mapToLong(PChartResponse.Point::defects).sum();

        tx.executeWithoutResult(status -> jdbc.update("UPDATE inspection SET final_prediction = 'none' WHERE id = ?", id));
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void invalidFiltersAreRejected() {
        assertInvalid(() -> spcService.pChart(0, null, null));
        assertInvalid(() -> spcService.commonality(14, "none"));
        assertInvalid(() -> spcService.commonality(14, "Unknown"));
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(WaferException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }
}
