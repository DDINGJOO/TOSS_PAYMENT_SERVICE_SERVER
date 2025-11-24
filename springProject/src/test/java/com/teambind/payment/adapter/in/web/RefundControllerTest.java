package com.teambind.payment.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teambind.payment.adapter.in.web.dto.RefundRequest;
import com.teambind.payment.application.service.RefundService;
import com.teambind.payment.common.exception.RefundException;
import com.teambind.payment.domain.Money;
import com.teambind.payment.domain.Payment;
import com.teambind.payment.domain.PaymentMethod;
import com.teambind.payment.domain.Refund;
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

@WebMvcTest(RefundController.class)
class RefundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RefundService refundService;

    @Test
    @DisplayName("환불 요청 API 성공")
    void requestRefund_success() throws Exception {
        // given
        RefundRequest request = new RefundRequest(
                "PAY-12345678",
                "고객 요청"
        );

        Payment payment = Payment.prepare("reservation-123", Money.of(100000L), LocalDateTime.now().plusDays(10));
        payment.complete("order-123", "payment-key-123", "transaction-123", PaymentMethod.CARD);

        Refund refund = Refund.request(payment.getPaymentId(), Money.of(100000L), Money.of(100000L), "고객 요청");
        refund.approve();
        refund.complete("refund-transaction-123");

        given(refundService.processRefund(eq(request.paymentId()), eq(request.reason())))
                .willReturn(refund);

        // when & then
        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value(refund.getRefundId()))
                .andExpect(jsonPath("$.paymentId").value(payment.getPaymentId()))
                .andExpect(jsonPath("$.originalAmount").value(100000L))
                .andExpect(jsonPath("$.refundAmount").value(100000L))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.reason").value("고객 요청"))
                .andExpect(jsonPath("$.transactionId").value("refund-transaction-123"));

        verify(refundService).processRefund(request.paymentId(), request.reason());
    }

    @Test
    @DisplayName("환불 요청 API 실패 - 필수 필드 누락 (paymentId)")
    void requestRefund_fail_missingPaymentId() throws Exception {
        // given
        String invalidRequest = """
                {
                    "reason": "고객 요청"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verify(refundService, never()).processRefund(any(), any());
    }

    @Test
    @DisplayName("환불 요청 API 실패 - 필수 필드 누락 (reason)")
    void requestRefund_fail_missingReason() throws Exception {
        // given
        String invalidRequest = """
                {
                    "paymentId": "PAY-12345678"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verify(refundService, never()).processRefund(any(), any());
    }

    @Test
    @DisplayName("환불 요청 API 실패 - 결제 정보를 찾을 수 없음")
    void requestRefund_fail_paymentNotFound() throws Exception {
        // given
        RefundRequest request = new RefundRequest(
                "PAY-12345678",
                "고객 요청"
        );

        given(refundService.processRefund(any(), any()))
                .willThrow(RefundException.notFound("PAY-12345678"));

        // when & then
        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("환불 요청 API 실패 - 환불 불가 상태")
    void requestRefund_fail_refundNotAllowed() throws Exception {
        // given
        RefundRequest request = new RefundRequest(
                "PAY-12345678",
                "고객 요청"
        );

        given(refundService.processRefund(any(), any()))
                .willThrow(RefundException.notAllowed("체크인 날짜 이후에는 환불이 불가능합니다"));

        // when & then
        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("환불 요청 API 실패 - 서버 오류")
    void requestRefund_fail_serverError() throws Exception {
        // given
        RefundRequest request = new RefundRequest(
                "PAY-12345678",
                "고객 요청"
        );

        given(refundService.processRefund(any(), any()))
                .willThrow(new RuntimeException("토스 API 호출 실패"));

        // when & then
        mockMvc.perform(post("/api/v1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("환불 조회 API 성공")
    void getRefund_success() throws Exception {
        // given
        String refundId = "REF-12345678";

        Payment payment = Payment.prepare("reservation-123", Money.of(100000L), LocalDateTime.now().plusDays(10));
        payment.complete("order-123", "payment-key-123", "transaction-123", PaymentMethod.CARD);

        Refund refund = Refund.request(payment.getPaymentId(), Money.of(100000L), Money.of(100000L), "고객 요청");
        refund.approve();
        refund.complete("refund-transaction-123");

        given(refundService.getRefund(eq(refundId)))
                .willReturn(refund);

        // when & then
        mockMvc.perform(get("/api/v1/refunds/{refundId}", refundId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value(refund.getRefundId()))
                .andExpect(jsonPath("$.paymentId").value(payment.getPaymentId()))
                .andExpect(jsonPath("$.originalAmount").value(100000L))
                .andExpect(jsonPath("$.refundAmount").value(100000L))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.reason").value("고객 요청"))
                .andExpect(jsonPath("$.transactionId").value("refund-transaction-123"));

        verify(refundService).getRefund(refundId);
    }

    @Test
    @DisplayName("환불 조회 API 실패 - 환불 정보를 찾을 수 없음")
    void getRefund_fail_notFound() throws Exception {
        // given
        String refundId = "REF-NOTFOUND";

        given(refundService.getRefund(any()))
                .willThrow(RefundException.notFound(refundId));

        // when & then
        mockMvc.perform(get("/api/v1/refunds/{refundId}", refundId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").exists());

        verify(refundService).getRefund(refundId);
    }

    @Test
    @DisplayName("환불 조회 API 성공 - 부분 환불")
    void getRefund_success_partialRefund() throws Exception {
        // given
        String refundId = "REF-12345678";

        Payment payment = Payment.prepare("reservation-123", Money.of(100000L), LocalDateTime.now().plusDays(5));
        payment.complete("order-123", "payment-key-123", "transaction-123", PaymentMethod.CARD);

        Refund refund = Refund.request(payment.getPaymentId(), Money.of(100000L), Money.of(70000L), "체크인 5일 전 취소");
        refund.approve();
        refund.complete("refund-transaction-123");

        given(refundService.getRefund(eq(refundId)))
                .willReturn(refund);

        // when & then
        mockMvc.perform(get("/api/v1/refunds/{refundId}", refundId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value(refund.getRefundId()))
                .andExpect(jsonPath("$.originalAmount").value(100000L))
                .andExpect(jsonPath("$.refundAmount").value(70000L))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(refundService).getRefund(refundId);
    }
}