package com.teambind.payment.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
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
 * 환불 E2E 테스트
 * - 결제 완료 상태 생성
 * - 환불 요청 → 예약 서비스 호출 (체크인 날짜 조회)
 * - 환불율 계산 → Toss 환불 API 호출
 * - Refund COMPLETED 상태 확인
 * - Outbox 이벤트 발행 확인
 */
@DisplayName("환불 E2E 테스트")
class RefundE2ETest extends AbstractE2ETest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("전체 환불 플로우 - 5일 전 취소 (100% 환불)")
    void fullRefundFlow_5DaysBefore_FullRefund() throws Exception {
        // Given 1: 결제 완료 상태 생성
        String reservationId = "RSV-REFUND-001";
        Long amount = 100000L;
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(10);
        String paymentId = createCompletedPayment(reservationId, amount, checkInDate);

        // Given 2: 예약 서비스 Mock 설정 (체크인 날짜 조회)
        getReservationServiceServer().stubFor(WireMock.get(urlPathEqualTo("/api/v1/reservations/" + reservationId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                    "reservationId": "%s",
                                    "checkInDate": "%s",
                                    "status": "CONFIRMED"
                                }
                                """, reservationId, checkInDate))));

        // Given 3: Toss 환불 API Mock 설정
        getTossApiServer().stubFor(WireMock.post(urlPathMatching("/v1/payments/.*/cancel"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                    "paymentKey": "test_payment_key",
                                    "orderId": "%s",
                                    "status": "CANCELED",
                                    "cancels": [{
                                        "cancelAmount": %d,
                                        "cancelReason": "고객 요청",
                                        "canceledAt": "%s"
                                    }]
                                }
                                """, reservationId, amount, LocalDateTime.now()))));

        // When: 환불 요청
        String refundRequest = """
                {
                    "reason": "고객 요청"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/refunds")
                        .param("paymentId", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").exists())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.refundAmount").value(amount)) // 100% 환불
                .andExpect(jsonPath("$.refundRate").value(1.0))
                .andReturn();

        // Then 1: Refund 엔티티 확인
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        String refundId = (String) response.get("refundId");

        Map<String, Object> refund = jdbcTemplate.queryForMap(
                "SELECT * FROM refunds WHERE refund_id = ?",
                refundId
        );

        assertThat(refund).isNotNull();
        assertThat(refund.get("status")).isEqualTo("COMPLETED");
        assertThat(refund.get("payment_id")).isEqualTo(paymentId);
        assertThat(((BigDecimal) refund.get("refund_amount")).longValue()).isEqualTo(amount);

        // Then 2: Payment 상태가 CANCELLED로 변경되었는지 확인
        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT * FROM payments WHERE payment_id = ?",
                paymentId
        );
        assertThat(payment.get("status")).isEqualTo("CANCELLED");
        assertThat(payment.get("cancelled_at")).isNotNull();

        // Then 3: Outbox 이벤트 발행 확인
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Integer eventCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM payment_events WHERE event_type = 'REFUND_COMPLETED'",
                            Integer.class
                    );
                    assertThat(eventCount).isGreaterThanOrEqualTo(1);
                });
    }

    @Test
    @DisplayName("환불 플로우 - 4일 전 취소 (70% 환불)")
    void fullRefundFlow_4DaysBefore_70PercentRefund() throws Exception {
        // Given: 결제 완료 상태 생성
        String reservationId = "RSV-REFUND-002";
        Long amount = 100000L;
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(4).plusHours(12); // 4일 후
        String paymentId = createCompletedPayment(reservationId, amount, checkInDate);

        // 예약 서비스 Mock
        getReservationServiceServer().stubFor(WireMock.get(urlPathEqualTo("/api/v1/reservations/" + reservationId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                    "reservationId": "%s",
                                    "checkInDate": "%s",
                                    "status": "CONFIRMED"
                                }
                                """, reservationId, checkInDate))));

        // Toss 환불 API Mock
        long expectedRefundAmount = (long) (amount * 0.70); // 70%
        getTossApiServer().stubFor(WireMock.post(urlPathMatching("/v1/payments/.*/cancel"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                    "paymentKey": "test_payment_key",
                                    "orderId": "%s",
                                    "status": "CANCELED",
                                    "cancels": [{
                                        "cancelAmount": %d,
                                        "cancelReason": "고객 변심",
                                        "canceledAt": "%s"
                                    }]
                                }
                                """, reservationId, expectedRefundAmount, LocalDateTime.now()))));

        // When: 환불 요청
        String refundRequest = """
                {
                    "reason": "고객 변심"
                }
                """;

        // Then: 70% 환불 확인
        mockMvc.perform(post("/api/v1/refunds")
                        .param("paymentId", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundAmount").value(expectedRefundAmount))
                .andExpect(jsonPath("$.refundRate").value(0.7));
    }

    @Test
    @DisplayName("환불 플로우 - 결제 후 10분 이내 취소 (수수료 면제)")
    void fullRefundFlow_Within10Minutes_CommissionFree() throws Exception {
        // Given: 방금 결제 완료된 상태 생성 (10분 이내)
        String reservationId = "RSV-REFUND-003";
        Long amount = 50000L;
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(3);
        String paymentId = createCompletedPayment(reservationId, amount, checkInDate);

        // 결제 시간을 현재로 업데이트 (10분 이내 시뮬레이션)
        jdbcTemplate.update(
                "UPDATE payments SET paid_at = ? WHERE payment_id = ?",
                LocalDateTime.now().minusMinutes(5), // 5분 전 결제
                paymentId
        );

        // 예약 서비스 Mock
        getReservationServiceServer().stubFor(WireMock.get(urlPathEqualTo("/api/v1/reservations/" + reservationId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                    "reservationId": "%s",
                                    "checkInDate": "%s",
                                    "status": "CONFIRMED"
                                }
                                """, reservationId, checkInDate))));

        // Toss 환불 API Mock
        getTossApiServer().stubFor(WireMock.post(urlPathMatching("/v1/payments/.*/cancel"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                    "paymentKey": "test_payment_key",
                                    "orderId": "%s",
                                    "status": "CANCELED",
                                    "cancels": [{
                                        "cancelAmount": %d,
                                        "cancelReason": "10분 이내 무료 취소",
                                        "canceledAt": "%s"
                                    }]
                                }
                                """, reservationId, amount, LocalDateTime.now()))));

        // When: 환불 요청
        String refundRequest = """
                {
                    "reason": "10분 이내 무료 취소"
                }
                """;

        // Then: 100% 환불 + 수수료 면제 확인
        mockMvc.perform(post("/api/v1/refunds")
                        .param("paymentId", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundRequest))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundAmount").value(amount))
                .andExpect(jsonPath("$.commissionFree").value(true));
    }

    @Test
    @DisplayName("환불 실패 - 체크인 날짜 이후")
    void refund_Fail_AfterCheckIn() throws Exception {
        // Given: 체크인 날짜가 지난 결제
        String reservationId = "RSV-REFUND-004";
        Long amount = 80000L;
        LocalDateTime pastCheckInDate = LocalDateTime.now().minusDays(1); // 어제 체크인
        String paymentId = createCompletedPayment(reservationId, amount, pastCheckInDate);

        // 예약 서비스 Mock
        getReservationServiceServer().stubFor(WireMock.get(urlPathEqualTo("/api/v1/reservations/" + reservationId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                    "reservationId": "%s",
                                    "checkInDate": "%s",
                                    "status": "CONFIRMED"
                                }
                                """, reservationId, pastCheckInDate))));

        // When: 환불 요청
        String refundRequest = """
                {
                    "reason": "환불 요청"
                }
                """;

        // Then: 400 Bad Request 예상
        mockMvc.perform(post("/api/v1/refunds")
                        .param("paymentId", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundRequest))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("환불 조회 API")
    void getRefund() throws Exception {
        // Given: 환불 완료 상태 생성
        String reservationId = "RSV-REFUND-005";
        Long amount = 60000L;
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(7);
        String paymentId = createCompletedPayment(reservationId, amount, checkInDate);

        // 예약 서비스 & Toss API Mock 설정
        setupMocksForRefund(reservationId, checkInDate, amount);

        // 환불 요청
        String refundRequest = """
                {
                    "reason": "테스트"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/refunds")
                        .param("paymentId", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundRequest))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        String refundId = (String) response.get("refundId");

        // When: 환불 조회
        mockMvc.perform(get("/api/v1/refunds/" + refundId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value(refundId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.refundAmount").exists());
    }

    /**
     * 헬퍼 메서드: 결제 완료 상태 생성
     */
    private String createCompletedPayment(String reservationId, Long amount, LocalDateTime checkInDate) throws Exception {
        // 1. Payment PREPARED 생성
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
                .andExpect(status().isCreated());

        // 2. Toss API Mock 설정
        String paymentKey = "test_payment_key_" + reservationId;
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
                                """, paymentKey, reservationId, amount, LocalDateTime.now()))));

        // 3. 결제 승인
        String confirmRequest = String.format("""
                {
                    "paymentKey": "%s",
                    "orderId": "%s",
                    "amount": %d
                }
                """, paymentKey, reservationId, amount);

        MvcResult result = mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        return (String) response.get("paymentId");
    }

    /**
     * 헬퍼 메서드: 환불을 위한 Mock 설정
     */
    private void setupMocksForRefund(String reservationId, LocalDateTime checkInDate, Long amount) {
        // 예약 서비스 Mock
        getReservationServiceServer().stubFor(WireMock.get(urlPathEqualTo("/api/v1/reservations/" + reservationId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                    "reservationId": "%s",
                                    "checkInDate": "%s",
                                    "status": "CONFIRMED"
                                }
                                """, reservationId, checkInDate))));

        // Toss 환불 API Mock
        getTossApiServer().stubFor(WireMock.post(urlPathMatching("/v1/payments/.*/cancel"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                    "paymentKey": "test_payment_key",
                                    "orderId": "%s",
                                    "status": "CANCELED",
                                    "cancels": [{
                                        "cancelAmount": %d,
                                        "cancelReason": "테스트",
                                        "canceledAt": "%s"
                                    }]
                                }
                                """, reservationId, amount, LocalDateTime.now()))));
    }
}