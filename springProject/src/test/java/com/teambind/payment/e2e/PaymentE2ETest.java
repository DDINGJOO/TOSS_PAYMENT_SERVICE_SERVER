package com.teambind.payment.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 E2E 테스트
 * - Kafka 이벤트 → Payment PREPARED
 * - API 결제 준비 → Payment PREPARED (Dual Path)
 * - 결제 승인 → Toss API 호출 → Payment COMPLETED
 * - Outbox 이벤트 발행 확인
 */
@DisplayName("결제 E2E 테스트")
class PaymentE2ETest extends AbstractE2ETest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("전체 결제 플로우 - Kafka 이벤트 경로")
    void fullPaymentFlow_ViaKafkaEvent() throws Exception {
        // Given
        String reservationId = "RSV-E2E-001";
        Long amount = 50000L;
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(10);

        // Kafka 이벤트 발행 (예약 확정 이벤트)
        Map<String, Object> eventPayload = Map.of(
                "reservationId", reservationId,
                "amount", amount,
                "checkInDate", checkInDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        kafkaTemplate.send("reservation-confirmed-test", eventPayload).get(); // Wait for message to be sent

        // When & Then 1: Payment PREPARED 상태로 저장 확인 (이벤트 컨슈머 처리 대기)
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM payments WHERE reservation_id = ? AND status = 'PREPARED'",
                            Integer.class,
                            reservationId
                    );
                    assertThat(count).isEqualTo(1);
                });

        // Given 2: Toss API Mock 설정
        String orderId = reservationId;
        String paymentKey = "test_payment_key_123";

        getTossApiServer().stubFor(WireMock.post(urlPathEqualTo("/v1/payments/confirm"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                    "paymentKey": "%s",
                                    "orderId": "%s",
                                    "transactionKey": "TXN-123",
                                    "method": "CARD",
                                    "totalAmount": %d,
                                    "status": "DONE",
                                    "approvedAt": "%s"
                                }
                                """, paymentKey, orderId, amount, LocalDateTime.now()))));

        // When 2: 결제 승인 API 호출
        String confirmRequest = String.format("""
                {
                    "paymentKey": "%s",
                    "orderId": "%s",
                    "amount": %d
                }
                """, paymentKey, orderId, amount);

        MvcResult result = mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(amount))
                .andReturn();

        // Then 2: Payment COMPLETED 상태 확인
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        String paymentId = (String) response.get("paymentId");

        assertThat(paymentId).isNotNull();

        // Then 3: 데이터베이스 확인
        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT * FROM payments WHERE payment_id = ?",
                paymentId
        );

        assertThat(payment).isNotNull();
        assertThat(payment.get("status")).isEqualTo("COMPLETED");
        assertThat(payment.get("reservation_id")).isEqualTo(reservationId);
        assertThat(payment.get("payment_key")).isEqualTo(paymentKey);
        assertThat(payment.get("order_id")).isEqualTo(orderId);
        assertThat(payment.get("paid_at")).isNotNull();

        // Then 4: Outbox 이벤트 발행 확인
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Integer eventCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM payment_events WHERE aggregate_id = ? AND event_type = 'PAYMENT_COMPLETED'",
                            Integer.class,
                            paymentId
                    );
                    assertThat(eventCount).isGreaterThanOrEqualTo(1);
                });
    }

    @Test
    @DisplayName("전체 결제 플로우 - REST API 경로 (Dual Path)")
    void fullPaymentFlow_ViaRestAPI() throws Exception {
        // Given
        String reservationId = "RSV-E2E-002";
        Long amount = 100000L;
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(5);

        // When 1: REST API로 결제 준비
        String prepareRequest = String.format("""
                {
                    "reservationId": "%s",
                    "amount": %d,
                    "checkInDate": "%s"
                }
                """, reservationId, amount, checkInDate);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prepareRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.status").value("PREPARED"))
                .andExpect(jsonPath("$.reservationId").value(reservationId));

        // Then 1: Payment PREPARED 상태 확인
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE reservation_id = ? AND status = 'PREPARED'",
                Integer.class,
                reservationId
        );
        assertThat(count).isEqualTo(1);

        // Given 2: Toss API Mock 설정
        String paymentKey = "test_payment_key_456";
        String orderId = reservationId;

        getTossApiServer().stubFor(WireMock.post(urlPathEqualTo("/v1/payments/confirm"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                    "paymentKey": "%s",
                                    "orderId": "%s",
                                    "transactionKey": "TXN-456",
                                    "method": "CARD",
                                    "totalAmount": %d,
                                    "status": "DONE",
                                    "approvedAt": "%s"
                                }
                                """, paymentKey, orderId, amount, LocalDateTime.now()))));

        // When 2: 결제 승인 API 호출
        String confirmRequest = String.format("""
                {
                    "paymentKey": "%s",
                    "orderId": "%s",
                    "amount": %d
                }
                """, paymentKey, orderId, amount);

        MvcResult result = mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();

        // Then 2: 결제 조회 API로 확인
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        String paymentId = (String) response.get("paymentId");

        mockMvc.perform(get("/api/v1/payments/" + paymentId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.paymentKey").value(paymentKey));
    }

    @Test
    @DisplayName("결제 승인 실패 - 금액 불일치")
    void paymentConfirm_Fail_AmountMismatch() throws Exception {
        // Given: Payment PREPARED 상태 생성
        String reservationId = "RSV-E2E-003";
        Long correctAmount = 50000L;
        Long wrongAmount = 45000L; // 잘못된 금액
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(7);

        String prepareRequest = String.format("""
                {
                    "reservationId": "%s",
                    "amount": %d,
                    "checkInDate": "%s"
                }
                """, reservationId, correctAmount, checkInDate);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prepareRequest))
                .andExpect(status().isCreated());

        // When: 잘못된 금액으로 결제 승인 시도
        String confirmRequest = String.format("""
                {
                    "paymentKey": "test_payment_key",
                    "orderId": "%s",
                    "amount": %d
                }
                """, reservationId, wrongAmount);

        // Then: 400 Bad Request 또는 409 Conflict 예상
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest))
                .andDo(print())
                .andExpect(status().is4xxClientError());

        // Then: Payment는 여전히 PREPARED 상태
        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT * FROM payments WHERE reservation_id = ?",
                reservationId
        );
        assertThat(payment.get("status")).isEqualTo("PREPARED");
    }

    @Test
    @DisplayName("결제 준비되지 않은 상태에서 승인 시도")
    void paymentConfirm_Fail_NotPrepared() throws Exception {
        // Given: 준비되지 않은 orderId
        String notPreparedOrderId = "RSV-NOT-EXISTS";

        String confirmRequest = String.format("""
                {
                    "paymentKey": "test_payment_key",
                    "orderId": "%s",
                    "amount": 50000
                }
                """, notPreparedOrderId);

        // When & Then: 404 Not Found 예상
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest))
                .andDo(print())
                .andExpect(status().isNotFound());
    }
}