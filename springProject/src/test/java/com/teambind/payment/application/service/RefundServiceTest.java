package com.teambind.payment.application.service;

import com.teambind.payment.adapter.out.toss.dto.TossRefundRequest;
import com.teambind.payment.adapter.out.toss.dto.TossRefundResponse;
import com.teambind.payment.application.port.out.PaymentRepository;
import com.teambind.payment.application.port.out.RefundRepository;
import com.teambind.payment.application.port.out.TossRefundClient;
import com.teambind.payment.common.exception.PaymentException;
import com.teambind.payment.common.exception.RefundException;
import com.teambind.payment.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private TossRefundClient tossRefundClient;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @InjectMocks
    private RefundService refundService;

    private Payment payment;
    private TossRefundResponse tossRefundResponse;

    @BeforeEach
    void setUp() {
        // 7일 후 체크인 예정 결제
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(10);
        payment = Payment.prepare("reservation-123", Money.of(100000L), checkInDate);
        payment.complete("order-123", "payment-key-123", "transaction-123", PaymentMethod.CARD);

        tossRefundResponse = new TossRefundResponse(
                "payment-key-123",
                "order-123",
                "refund-transaction-123",
                100000L,
                "CANCELED",
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("환불 처리 성공 - 100% 환불 (7일 이상)")
    void processRefund_success_fullRefund() {
        // given
        String paymentId = payment.getPaymentId();
        String reason = "고객 요청";

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(refundRepository.save(any(Refund.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(tossRefundClient.cancelPayment(eq(payment.getPaymentKey()), any(TossRefundRequest.class)))
                .willReturn(tossRefundResponse);
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Refund result = refundService.processRefund(paymentId, reason);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(result.getRefundAmount()).isEqualTo(Money.of(100000L));
        assertThat(result.getOriginalAmount()).isEqualTo(Money.of(100000L));
        assertThat(result.getReason()).isEqualTo(reason);
        assertThat(result.getTransactionId()).isEqualTo("refund-transaction-123");

        verify(paymentRepository).findById(paymentId);
        verify(refundRepository, times(2)).save(any(Refund.class));
        verify(tossRefundClient).cancelPayment(eq(payment.getPaymentKey()), any(TossRefundRequest.class));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("환불 처리 성공 - 50% 환불 (3-7일)")
    void processRefund_success_partialRefund() {
        // given
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(5);
        Payment partialRefundPayment = Payment.prepare("reservation-456", Money.of(100000L), checkInDate);
        partialRefundPayment.complete("order-456", "payment-key-456", "transaction-456", PaymentMethod.CARD);

        TossRefundResponse partialResponse = new TossRefundResponse(
                "payment-key-456",
                "order-456",
                "refund-transaction-456",
                50000L,
                "CANCELED",
                LocalDateTime.now()
        );

        given(paymentRepository.findById(partialRefundPayment.getPaymentId()))
                .willReturn(Optional.of(partialRefundPayment));
        given(refundRepository.save(any(Refund.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(tossRefundClient.cancelPayment(eq(partialRefundPayment.getPaymentKey()), any(TossRefundRequest.class)))
                .willReturn(partialResponse);
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Refund result = refundService.processRefund(partialRefundPayment.getPaymentId(), "고객 요청");

        // then
        assertThat(result.getRefundAmount().getValue().longValue()).isEqualTo(50000L);
        assertThat(result.getOriginalAmount().getValue().longValue()).isEqualTo(100000L);
        assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("환불 처리 실패 - 결제 정보를 찾을 수 없음")
    void processRefund_fail_paymentNotFound() {
        // given
        String paymentId = "non-existent-id";
        given(paymentRepository.findById(paymentId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> refundService.processRefund(paymentId, "고객 요청"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Payment not found");

        verify(paymentRepository).findById(paymentId);
        verify(refundRepository, never()).save(any());
        verify(tossRefundClient, never()).cancelPayment(any(), any());
    }

    @Test
    @DisplayName("환불 처리 실패 - 환불 불가 기간 (3일 미만)")
    void processRefund_fail_withinThreeDays() {
        // given
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(2);
        Payment recentPayment = Payment.prepare("reservation-999", Money.of(100000L), checkInDate);
        recentPayment.complete("order-999", "payment-key-999", "transaction-999", PaymentMethod.CARD);

        given(paymentRepository.findById(recentPayment.getPaymentId()))
                .willReturn(Optional.of(recentPayment));

        // when & then
        assertThatThrownBy(() -> refundService.processRefund(recentPayment.getPaymentId(), "고객 요청"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("환불이 불가능합니다");
    }

    @Test
    @DisplayName("환불 처리 실패 - Toss API 호출 실패")
    void processRefund_fail_tossApiError() {
        // given
        String paymentId = payment.getPaymentId();
        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(refundRepository.save(any(Refund.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(tossRefundClient.cancelPayment(eq(payment.getPaymentKey()), any(TossRefundRequest.class)))
                .willThrow(new RuntimeException("토스 API 오류"));

        // when & then
        assertThatThrownBy(() -> refundService.processRefund(paymentId, "고객 요청"))
                .isInstanceOf(RefundException.class)
                .hasMessageContaining("Refund processing failed");

        verify(refundRepository, times(2)).save(any(Refund.class));
    }

    @Test
    @DisplayName("환불 처리 실패 - COMPLETED 상태가 아닌 결제")
    void processRefund_fail_notCompletedPayment() {
        // given
        Payment preparedPayment = Payment.prepare("reservation-555", Money.of(100000L), LocalDateTime.now().plusDays(10));

        given(paymentRepository.findById(preparedPayment.getPaymentId()))
                .willReturn(Optional.of(preparedPayment));

        // when & then
        assertThatThrownBy(() -> refundService.processRefund(preparedPayment.getPaymentId(), "고객 요청"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 결제만 환불 가능합니다");
    }
}