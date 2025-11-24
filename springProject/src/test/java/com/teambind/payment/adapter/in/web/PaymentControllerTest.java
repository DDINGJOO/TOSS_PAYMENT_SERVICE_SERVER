package com.teambind.payment.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teambind.payment.adapter.in.web.dto.PaymentCancelRequest;
import com.teambind.payment.adapter.in.web.dto.PaymentConfirmRequest;
import com.teambind.payment.adapter.in.web.dto.PaymentPrepareRequest;
import com.teambind.payment.application.service.PaymentCancelService;
import com.teambind.payment.application.service.PaymentConfirmService;
import com.teambind.payment.application.service.PaymentPrepareService;
import com.teambind.payment.common.exception.PaymentException;
import com.teambind.payment.domain.Money;
import com.teambind.payment.domain.Payment;
import com.teambind.payment.domain.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentPrepareService paymentPrepareService;

    @MockBean
    private PaymentConfirmService paymentConfirmService;

    @MockBean
    private PaymentCancelService paymentCancelService;

    @Test
    @DisplayName("결제 승인 API 성공")
    void confirmPayment_success() throws Exception {
        // given
        PaymentConfirmRequest request = new PaymentConfirmRequest(
                "PAY-12345678",
                "order-123",
                "payment-key-123",
                100000L
        );

        Payment completedPayment = Payment.prepare("reservation-123", Money.of(100000L), LocalDateTime.now().plusDays(7));
        completedPayment.complete("order-123", "payment-key-123", "transaction-123", PaymentMethod.CARD);

        given(paymentConfirmService.confirmPayment(
                eq(request.paymentId()),
                eq(request.orderId()),
                eq(request.paymentKey()),
                eq(request.amount())
        )).willReturn(completedPayment);

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(completedPayment.getPaymentId()))
                .andExpect(jsonPath("$.reservationId").value("reservation-123"))
                .andExpect(jsonPath("$.orderId").value("order-123"))
                .andExpect(jsonPath("$.paymentKey").value("payment-key-123"))
                .andExpect(jsonPath("$.transactionId").value("transaction-123"))
                .andExpect(jsonPath("$.amount").value(100000L))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(paymentConfirmService).confirmPayment(
                request.paymentId(),
                request.orderId(),
                request.paymentKey(),
                request.amount()
        );
    }

    @Test
    @DisplayName("결제 승인 API 실패 - 필수 필드 누락 (paymentId)")
    void confirmPayment_fail_missingPaymentId() throws Exception {
        // given
        String invalidRequest = """
                {
                    "orderId": "order-123",
                    "paymentKey": "payment-key-123",
                    "amount": 100000
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verify(paymentConfirmService, never()).confirmPayment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("결제 승인 API 실패 - 필수 필드 누락 (orderId)")
    void confirmPayment_fail_missingOrderId() throws Exception {
        // given
        String invalidRequest = """
                {
                    "paymentId": "PAY-12345678",
                    "paymentKey": "payment-key-123",
                    "amount": 100000
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verify(paymentConfirmService, never()).confirmPayment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("결제 승인 API 실패 - 필수 필드 누락 (paymentKey)")
    void confirmPayment_fail_missingPaymentKey() throws Exception {
        // given
        String invalidRequest = """
                {
                    "paymentId": "PAY-12345678",
                    "orderId": "order-123",
                    "amount": 100000
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verify(paymentConfirmService, never()).confirmPayment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("결제 승인 API 실패 - 필수 필드 누락 (amount)")
    void confirmPayment_fail_missingAmount() throws Exception {
        // given
        String invalidRequest = """
                {
                    "paymentId": "PAY-12345678",
                    "orderId": "order-123",
                    "paymentKey": "payment-key-123"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verify(paymentConfirmService, never()).confirmPayment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("결제 승인 API 실패 - 금액이 0 이하")
    void confirmPayment_fail_invalidAmount() throws Exception {
        // given
        PaymentConfirmRequest invalidRequest = new PaymentConfirmRequest(
                "PAY-12345678",
                "order-123",
                "payment-key-123",
                -1000L
        );

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(paymentConfirmService, never()).confirmPayment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("결제 승인 API 실패 - 결제 정보를 찾을 수 없음")
    void confirmPayment_fail_paymentNotFound() throws Exception {
        // given
        PaymentConfirmRequest request = new PaymentConfirmRequest(
                "PAY-12345678",
                "order-123",
                "payment-key-123",
                100000L
        );

        given(paymentConfirmService.confirmPayment(any(), any(), any(), any()))
                .willThrow(PaymentException.notFound("PAY-12345678"));

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("결제 승인 API 실패 - 금액 불일치")
    void confirmPayment_fail_amountMismatch() throws Exception {
        // given
        PaymentConfirmRequest request = new PaymentConfirmRequest(
                "PAY-12345678",
                "order-123",
                "payment-key-123",
                100000L
        );

        given(paymentConfirmService.confirmPayment(any(), any(), any(), any()))
                .willThrow(PaymentException.amountMismatch(100000L, 90000L));

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("결제 승인 API 실패 - 서버 오류")
    void confirmPayment_fail_serverError() throws Exception {
        // given
        PaymentConfirmRequest request = new PaymentConfirmRequest(
                "PAY-12345678",
                "order-123",
                "payment-key-123",
                100000L
        );

        given(paymentConfirmService.confirmPayment(any(), any(), any(), any()))
                .willThrow(new RuntimeException("토스 API 호출 실패"));

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("결제 승인 API - 간편결제 성공")
    void confirmPayment_success_easyPay() throws Exception {
        // given
        PaymentConfirmRequest request = new PaymentConfirmRequest(
                "PAY-12345678",
                "order-123",
                "payment-key-123",
                100000L
        );

        Payment completedPayment = Payment.prepare("reservation-123", Money.of(100000L), LocalDateTime.now().plusDays(7));
        completedPayment.complete("order-123", "payment-key-123", "transaction-123", PaymentMethod.EASY_PAY);

        given(paymentConfirmService.confirmPayment(any(), any(), any(), any()))
                .willReturn(completedPayment);

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("EASY_PAY"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("결제 승인 API - 가상계좌 성공")
    void confirmPayment_success_virtualAccount() throws Exception {
        // given
        PaymentConfirmRequest request = new PaymentConfirmRequest(
                "PAY-12345678",
                "order-123",
                "payment-key-123",
                100000L
        );

        Payment completedPayment = Payment.prepare("reservation-123", Money.of(100000L), LocalDateTime.now().plusDays(7));
        completedPayment.complete("order-123", "payment-key-123", "transaction-123", PaymentMethod.VIRTUAL_ACCOUNT);

        given(paymentConfirmService.confirmPayment(any(), any(), any(), any()))
                .willReturn(completedPayment);

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("VIRTUAL_ACCOUNT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("결제 준비 API 성공")
    void preparePayment_success() throws Exception {
        // given
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(7);
        PaymentPrepareRequest request = new PaymentPrepareRequest(
                "reservation-123",
                100000L,
                checkInDate
        );

        Payment preparedPayment = Payment.prepare("reservation-123", Money.of(100000L), checkInDate);

        given(paymentPrepareService.preparePayment(
                eq(request.reservationId()),
                eq(request.amount()),
                eq(request.checkInDate())
        )).willReturn(preparedPayment);

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(preparedPayment.getPaymentId()))
                .andExpect(jsonPath("$.reservationId").value("reservation-123"))
                .andExpect(jsonPath("$.amount").value(100000L))
                .andExpect(jsonPath("$.status").value("PREPARED"))
                .andExpect(jsonPath("$.idempotencyKey").value(preparedPayment.getIdempotencyKey()));

        verify(paymentPrepareService).preparePayment(
                request.reservationId(),
                request.amount(),
                request.checkInDate()
        );
    }

    @Test
    @DisplayName("결제 준비 API 성공 - 당일 예약")
    void preparePayment_success_sameDayReservation() throws Exception {
        // given
        LocalDateTime checkInDate = LocalDateTime.now().plusSeconds(10);  // 약간의 여유를 둠
        PaymentPrepareRequest request = new PaymentPrepareRequest(
                "reservation-123",
                100000L,
                checkInDate
        );

        Payment preparedPayment = Payment.prepare("reservation-123", Money.of(100000L), checkInDate);

        given(paymentPrepareService.preparePayment(any(), any(), any()))
                .willReturn(preparedPayment);

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PREPARED"));
    }

    @Test
    @DisplayName("결제 준비 API 실패 - 필수 필드 누락 (reservationId)")
    void preparePayment_fail_missingReservationId() throws Exception {
        // given
        String invalidRequest = """
                {
                    "amount": 100000,
                    "checkInDate": "2025-12-01T15:00:00"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verify(paymentPrepareService, never()).preparePayment(any(), any(), any());
    }

    @Test
    @DisplayName("결제 준비 API 실패 - 금액이 0 이하")
    void preparePayment_fail_invalidAmount() throws Exception {
        // given
        PaymentPrepareRequest invalidRequest = new PaymentPrepareRequest(
                "reservation-123",
                -1000L,
                LocalDateTime.now().plusDays(1)
        );

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(paymentPrepareService, never()).preparePayment(any(), any(), any());
    }

    @Test
    @DisplayName("결제 준비 API 실패 - 과거 날짜")
    void preparePayment_fail_pastDate() throws Exception {
        // given
        PaymentPrepareRequest invalidRequest = new PaymentPrepareRequest(
                "reservation-123",
                100000L,
                LocalDateTime.now().minusDays(1)
        );

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(paymentPrepareService, never()).preparePayment(any(), any(), any());
    }

    @Test
    @DisplayName("결제 취소 API 성공")
    void cancelPayment_success() throws Exception {
        // given
        String paymentId = "PAY-12345678";
        PaymentCancelRequest request = new PaymentCancelRequest("고객 요청");

        Payment payment = Payment.prepare("reservation-123", Money.of(100000L), LocalDateTime.now().plusDays(7));
        payment.complete("order-123", "payment-key-123", "transaction-123", PaymentMethod.CARD);
        payment.cancel();

        given(paymentCancelService.cancelPayment(eq(paymentId), eq(request.reason())))
                .willReturn(payment);

        // when & then
        mockMvc.perform(post("/api/v1/payments/{paymentId}/cancel", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(payment.getPaymentId()))  // 동적 생성된 ID 사용
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reservationId").value("reservation-123"))
                .andExpect(jsonPath("$.cancelledAt").exists());

        verify(paymentCancelService).cancelPayment(paymentId, request.reason());
    }

    @Test
    @DisplayName("결제 취소 API 실패 - 필수 필드 누락 (reason)")
    void cancelPayment_fail_missingReason() throws Exception {
        // given
        String paymentId = "PAY-12345678";
        String invalidRequest = "{}";

        // when & then
        mockMvc.perform(post("/api/v1/payments/{paymentId}/cancel", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verify(paymentCancelService, never()).cancelPayment(any(), any());
    }

    @Test
    @DisplayName("결제 조회 API 성공")
    void getPayment_success() throws Exception {
        // given
        String paymentId = "PAY-12345678";

        Payment payment = Payment.prepare("reservation-123", Money.of(100000L), LocalDateTime.now().plusDays(7));
        payment.complete("order-123", "payment-key-123", "transaction-123", PaymentMethod.CARD);

        given(paymentPrepareService.getPayment(eq(paymentId)))
                .willReturn(payment);

        // when & then
        mockMvc.perform(get("/api/v1/payments/{paymentId}", paymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(payment.getPaymentId()))  // 동적 생성된 ID 사용
                .andExpect(jsonPath("$.reservationId").value("reservation-123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(100000L));

        verify(paymentPrepareService).getPayment(paymentId);
    }

    @Test
    @DisplayName("결제 조회 API 실패 - 결제 정보를 찾을 수 없음")
    void getPayment_fail_notFound() throws Exception {
        // given
        String paymentId = "PAY-NOTFOUND";

        given(paymentPrepareService.getPayment(any()))
                .willThrow(PaymentException.notFound(paymentId));

        // when & then
        mockMvc.perform(get("/api/v1/payments/{paymentId}", paymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").exists());
    }
}