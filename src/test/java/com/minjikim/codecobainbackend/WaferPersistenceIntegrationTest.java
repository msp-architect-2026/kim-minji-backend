package com.minjikim.codecobainbackend;

import com.minjikim.codecobainbackend.dto.response.DailyStatsDto;
import com.minjikim.codecobainbackend.dto.response.DefectDistributionDto;
import com.minjikim.codecobainbackend.dto.response.StatsResponseDto;
import com.minjikim.codecobainbackend.service.StatsService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// 실제 MySQL로 Flyway 마이그레이션 + Hibernate validate + 통계 쿼리를 검증한다. Docker가 없으면 건너뛴다
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class WaferPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private StatsService statsService;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM wafer_image");
        insert("Center", 0.8, today.atTime(10, 0));
        insert("none", 1.0, today.atTime(11, 0));
        insert(null, null, today.atTime(12, 0));
        insert("Scratch", 0.6, today.minusDays(1).atTime(9, 0));
    }

    @Test
    void overallStatsMatchOriginalInMemoryCalculation() {
        StatsResponseDto stats = statsService.getStats();

        assertThat(stats.getTotalCount()).isEqualTo(4);
        assertThat(stats.getDefectCount()).isEqualTo(2);
        assertThat(stats.getDefectRate()).isCloseTo(50.0, within(0.001));
        assertThat(stats.getAvgConfidence()).isCloseTo(0.8, within(0.001));
    }

    @Test
    void dailyStatsAreGroupedAndSortedByDate() {
        List<DailyStatsDto> daily = statsService.getDailyStats(7);

        assertThat(daily).hasSize(2);
        assertThat(daily.get(0).getDate()).isEqualTo(today.minusDays(1).toString());
        assertThat(daily.get(0).getDefectCount()).isEqualTo(1);
        assertThat(daily.get(1).getDate()).isEqualTo(today.toString());
        assertThat(daily.get(1).getTotalCount()).isEqualTo(3);
        assertThat(daily.get(1).getDefectCount()).isEqualTo(1);
    }

    @Test
    void distributionExcludesNullButKeepsItInDenominator() {
        List<DefectDistributionDto> distribution = statsService.getDefectDistribution();

        assertThat(distribution).hasSize(3);
        assertThat(distribution).allSatisfy(d -> {
            assertThat(d.getCount()).isEqualTo(1);
            assertThat(d.getRatio()).isCloseTo(25.0, within(0.001));
        });
    }

    @Test
    void existingHibernateSchemaIsBaselinedAndGetsIndexes() throws Exception {
        String rootUrl = mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/legacy");
        try (Connection root = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
             Statement statement = root.createStatement()) {
            statement.execute("CREATE DATABASE legacy");
            statement.execute("""
                    CREATE TABLE legacy.wafer_image (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        filename VARCHAR(255), filepath VARCHAR(255), created_at DATETIME(6),
                        prediction VARCHAR(255), confidence DOUBLE
                    )""");
            statement.execute("INSERT INTO legacy.wafer_image (filename) VALUES ('kept')");

            Flyway.configure().dataSource(rootUrl, "root", mysql.getPassword())
                    .baselineOnMigrate(true).baselineVersion("1").load().migrate();

            try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM legacy.wafer_image")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (ResultSet indexes = statement.executeQuery("""
                    SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                    WHERE table_schema = 'legacy' AND table_name = 'wafer_image'
                    AND index_name LIKE 'idx_wafer_image_%'""")) {
                indexes.next();
                assertThat(indexes.getInt(1)).isEqualTo(2);
            }
        }
    }

    private void insert(String prediction, Double confidence, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO wafer_image (filename, filepath, created_at, prediction, confidence) VALUES ('f', 'p', ?, ?, ?)",
                createdAt, prediction, confidence);
    }
}
