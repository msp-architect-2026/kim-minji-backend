package com.minjikim.codecobainbackend;

import com.minjikim.codecobainbackend.dto.request.LotCreateRequest;
import com.minjikim.codecobainbackend.dto.response.AiPredictionResponse;
import com.minjikim.codecobainbackend.dto.response.InspectionResponse;
import com.minjikim.codecobainbackend.dto.response.LotDetailResponse;
import com.minjikim.codecobainbackend.entity.LotStatus;
import com.minjikim.codecobainbackend.entity.WaferStatus;
import com.minjikim.codecobainbackend.service.AiAnalysisService;
import com.minjikim.codecobainbackend.service.InspectionService;
import com.minjikim.codecobainbackend.service.LotService;
import com.minjikim.codecobainbackend.util.MinioUploader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// 부하 테스트에서 발견: 같은 Lot의 웨이퍼 판정이 동시에 끝나면 Lot 상태 갱신이 서로의 웨이퍼 행 잠금을 기다려 데드락이 났다
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ConcurrentJudgementIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static MockMultipartFile image;

    @Autowired private LotService lotService;
    @Autowired private InspectionService inspectionService;
    @MockBean private MinioUploader uploader;
    @MockBean private AiAnalysisService ai;

    @BeforeAll
    static void createImage() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", bytes);
        image = new MockMultipartFile("file", "w.png", "image/png", bytes.toByteArray());
    }

    @RepeatedTest(3)
    void allWafersOfOneLotCanBeJudgedConcurrently() throws Exception {
        when(uploader.upload(any(), anyString())).thenReturn("key");
        when(ai.analyze("key")).thenReturn(new AiPredictionResponse(null, "none", 0.95));
        Long lotId = lotService.create(new LotCreateRequest("C-" + UUID.randomUUID().toString().substring(0, 8), "P", 25)).id();

        ExecutorService pool = Executors.newFixedThreadPool(25);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<InspectionResponse>> calls = IntStream.rangeClosed(1, 25)
                .mapToObj(slot -> pool.submit(() -> {
                    start.await();
                    return inspectionService.inspectWafer(lotId, slot, image, null);
                }))
                .toList();
        start.countDown();

        long failed = 0;
        for (Future<InspectionResponse> call : calls) {
            try {
                call.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failed++;
                System.out.println("판정 실패: " + e.getCause());
            }
        }
        pool.shutdown();

        LotDetailResponse lot = lotService.detail(lotId);
        assertThat(failed).as("동시 판정 중 실패한 요청 수").isZero();
        assertThat(lot.wafers()).allMatch(wafer -> wafer.status() == WaferStatus.PASS);
        assertThat(lot.status()).isEqualTo(LotStatus.COMPLETED);
    }
}
