package com.teambind.payment.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * E2E 테스트를 위한 베이스 클래스
 * - Testcontainers로 실제 MariaDB, Kafka 환경 구성
 * - WireMock으로 외부 API (Toss, Reservation Service) 모킹
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractE2ETest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    // Testcontainers
    @Container
    protected static final MariaDBContainer<?> mariaDB = new MariaDBContainer<>(
            DockerImageName.parse("mariadb:11")
    )
            .withDatabaseName("payment_service_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @Container
    protected static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    )
            .withReuse(true);

    // WireMock Servers
    protected static WireMockServer tossApiServer;
    protected static WireMockServer reservationServiceServer;

    @BeforeAll
    static void beforeAll() {
        // WireMock 서버 시작 (Toss API Mock)
        tossApiServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        tossApiServer.start();
        WireMock.configureFor("localhost", tossApiServer.port());

        // WireMock 서버 시작 (Reservation Service Mock)
        reservationServiceServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        reservationServiceServer.start();
    }

    @AfterAll
    static void afterAll() {
        if (tossApiServer != null && tossApiServer.isRunning()) {
            tossApiServer.stop();
        }
        if (reservationServiceServer != null && reservationServiceServer.isRunning()) {
            reservationServiceServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // WireMock 리셋
        tossApiServer.resetAll();
        reservationServiceServer.resetAll();

        // 데이터베이스 클린업
        cleanDatabase();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // MariaDB 설정
        registry.add("spring.datasource.url", mariaDB::getJdbcUrl);
        registry.add("spring.datasource.username", mariaDB::getUsername);
        registry.add("spring.datasource.password", mariaDB::getPassword);
        registry.add("spring.datasource.driver-class-name", mariaDB::getDriverClassName);

        // Kafka 설정
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers);

        // Toss API Mock 서버
        registry.add("toss.payments.api.base-url", () -> "http://localhost:" + tossApiServer.port());

        // Reservation Service Mock 서버
        registry.add("reservation.service.url", () -> "http://localhost:" + reservationServiceServer.port());

        // Flyway 설정
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
    }

    /**
     * 테스트 간 데이터베이스 클린업
     */
    private void cleanDatabase() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE refunds");
        jdbcTemplate.execute("TRUNCATE TABLE payment_events");
        jdbcTemplate.execute("TRUNCATE TABLE payments");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    /**
     * WireMock 헬퍼 메서드
     */
    protected WireMockServer getTossApiServer() {
        return tossApiServer;
    }

    protected WireMockServer getReservationServiceServer() {
        return reservationServiceServer;
    }
}