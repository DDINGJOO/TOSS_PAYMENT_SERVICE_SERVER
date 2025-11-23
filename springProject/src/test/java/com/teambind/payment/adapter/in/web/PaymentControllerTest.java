package com.teambind.payment.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teambind.payment.adapter.in.web.dto.PaymentConfirmRequest;
import com.teambind.payment.application.service.PaymentConfirmService;
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
    private PaymentConfirmService paymentConfirmService;

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
                .willThrow(new IllegalArgumentException("결제 정보를 찾을 수 없습니다: PAY-12345678"));

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("결제 정보를 찾을 수 없습니다: PAY-12345678"));
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
                .willThrow(new IllegalArgumentException("금액이 일치하지 않습니다"));

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("금액이 일치하지 않습니다"));
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
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
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
}