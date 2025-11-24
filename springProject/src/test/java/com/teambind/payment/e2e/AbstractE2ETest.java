package com.teambind.payment.e2e;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

/**
 * E2E 테스트를 위한 베이스 클래스
 * - docker-compose로 실행된 MariaDB, Kafka 환경 사용
 * - WireMock으로 외부 API (Toss, Reservation Service) 모킹
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractE2ETest {

    @Autowired
    protected MockMvc mockMvc;

    protected KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

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

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // WireMock 동적 포트 설정
        registry.add("toss.payments.api.base-url", () -> "http://localhost:" + tossApiServer.port());
        registry.add("reservation.service.url", () -> "http://localhost:" + reservationServiceServer.port());
    }

    @BeforeEach
    void setUp() {
        // Kafka Producer 직접 생성 (docker-compose의 localhost:9092 사용)
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        ProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(props);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // WireMock 리셋
        tossApiServer.resetAll();
        reservationServiceServer.resetAll();

        // 데이터베이스 클린업
        cleanDatabase();
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