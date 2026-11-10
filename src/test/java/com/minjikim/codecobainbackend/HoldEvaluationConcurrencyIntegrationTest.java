package com.minjikim.codecobainbackend;

import com.minjikim.codecobainbackend.dto.request.LotCreateRequest;
import com.minjikim.codecobainbackend.dto.request.ReviewRequest;
import com.minjikim.codecobainbackend.dto.response.AiPredictionResponse;
import com.minjikim.codecobainbackend.entity.HoldSource;
import com.minjikim.codecobainbackend.entity.LotStatus;
import com.minjikim.codecobainbackend.repository.LotHoldRepository;
import com.minjikim.codecobainbackend.service.AiAnalysisService;
import com.minjikim.codecobainbackend.service.InspectionReviewService;
import com.minjikim.codecobainbackend.service.InspectionService;
import com.minjikim.codecobainbackend.service.LotService;
import com.minjikim.codecobainbackend.util.MinioUploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// 같은 Lot의 마지막 웨이퍼 두 장이 동시에 결함으로 판정되는 경우.
// 25장 중 8장 결함(p=0.0005)은 자동 Hold 대상이지만, 각 판정이 24장 중 7장(p=0.0021)만 보고 Hold 없이 Lot을 완료하면 안 된다.
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class HoldEvaluationConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.46");

    private static final int WAFERS = 25;
    private static final int DEFECTS_BEFORE_LAST_TWO = 6;

    @Autowired private LotService lotService;
    @Autowired private InspectionService inspectionService;
    @Autowired private InspectionReviewService reviewService;
    @Autowired private LotHoldRepository holds;
    @MockBean private MinioUploader uploader;
    @MockBean private AiAnalysisService ai;

    private MockMultipartFile pass;
    private MockMultipartFile defect;
    private MockMultipartFile uncertain;

    @BeforeEach
    void setUp() throws IOException {
        pass = image("pass.png");
        defect = image("defect.png");
        uncertain = image("uncertain.png");
        when(uploader.upload(any(), anyString())).thenAnswer(call -> call.getArgument(0, MockMultipartFile.class).getOriginalFilename());
        when(ai.analyze("pass.png")).thenReturn(new AiPredictionResponse(null, "none", 0.95));
        when(ai.analyze("defect.png")).thenReturn(new AiPredictionResponse(null, "Scratch", 0.95));
        when(ai.analyze("uncertain.png")).thenReturn(new AiPredictionResponse(null, "Scratch", 0.5));
    }

    @Test
    void lastTwoDefectsJudgedOneAfterAnotherPutLotOnHold() {
        Long lotId = lotWithFirst23Judged();

        inspectionService.inspectWafer(lotId, 24, defect, null);
        inspectionService.inspectWafer(lotId, 25, defect, null);

        assertThat(lotService.detail(lotId).status()).isEqualTo(LotStatus.HOLD);
    }

    @Test
    void lastTwoDefectsJudgedConcurrentlyPutLotOnHold() throws Exception {
        Long lotId = lotWithFirst23Judged();

        runWhileOthersWaitForLot(lotId,
                () -> inspectionService.inspectWafer(lotId, 24, defect, null),
                () -> inspectionService.inspectWafer(lotId, 25, defect, null));

        assertLotOnAutoHold(lotId);
    }

    // 엔지니어 리뷰 확정도 같은 Lot 판정과 동시에 끝날 수 있다
    @Test
    void reviewConfirmedConcurrentlyWithLastDefectPutsLotOnHold() throws Exception {
        Long lotId = lotWithFirst23Judged();
        Long pending = inspectionService.inspectWafer(lotId, 25, uncertain, null).id();

        runWhileOthersWaitForLot(lotId,
                () -> inspectionService.inspectWafer(lotId, 24, defect, null),
                () -> reviewService.review(pending, new ReviewRequest("Scratch", "kim", null)));

        assertLotOnAutoHold(lotId);
    }

    private void assertLotOnAutoHold(Long lotId) {
        assertSoftly(softly -> {
            softly.assertThat(holds.existsByLotIdAndSource(lotId, HoldSource.AUTO)).as("자동 Hold 이력").isTrue();
            softly.assertThat(lotService.detail(lotId).status()).as("Lot 상태").isEqualTo(LotStatus.HOLD);
        });
    }

    // 다른 트랜잭션이 Lot을 잠근 상태에서 작업을 보내 모두 Lot 잠금을 기다리게 한 뒤 놓아준다
    private void runWhileOthersWaitForLot(Long lotId, Runnable... tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        try (Connection root = rootConnection()) {
            root.setAutoCommit(false);
            try (PreparedStatement lock = root.prepareStatement("SELECT id FROM lot WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, lotId);
                lock.executeQuery().close();
            }
            List<Future<?>> running = Arrays.stream(tasks).<Future<?>>map(pool::submit).toList();
            awaitLockWaits(root, tasks.length);
            root.commit();
            for (Future<?> task : running) {
                task.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }
    }

    // 양품 17장 → 결함 6장 순서로 판정하면 23장 시점까지 Hold 조건(p < 0.001)에 걸리지 않는다
    private Long lotWithFirst23Judged() {
        Long lotId = lotService.create(new LotCreateRequest("H-" + UUID.randomUUID().toString().substring(0, 8), "P", WAFERS)).id();
        int passes = WAFERS - 2 - DEFECTS_BEFORE_LAST_TWO;
        for (int slot = 1; slot <= WAFERS - 2; slot++) {
            inspectionService.inspectWafer(lotId, slot, slot <= passes ? pass : defect, null);
        }
        assertThat(lotService.detail(lotId).status()).isEqualTo(LotStatus.IN_PROGRESS);
        return lotId;
    }

    // 잠금을 기다리는 판정 트랜잭션은 processlist에 Lot FOR UPDATE 쿼리로 보인다
    private static void awaitLockWaits(Connection root, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            try (ResultSet rs = root.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE info LIKE 'select % from lot % for update'")) {
                rs.next();
                if (rs.getInt(1) >= expected) {
                    return;
                }
            }
            Thread.sleep(20);
        }
        throw new IllegalStateException("Lot 잠금 대기가 " + expected + "건이 되지 않았습니다");
    }

    private static Connection rootConnection() throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
    }

    private static MockMultipartFile image(String name) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("file", name, "image/png", bytes.toByteArray());
    }
}
