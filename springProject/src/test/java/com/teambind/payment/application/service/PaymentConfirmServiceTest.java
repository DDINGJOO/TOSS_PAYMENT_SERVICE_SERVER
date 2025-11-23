package com.teambind.payment.application.service;

import com.teambind.payment.adapter.out.toss.dto.TossPaymentConfirmRequest;
import com.teambind.payment.adapter.out.toss.dto.TossPaymentConfirmResponse;
import com.teambind.payment.application.port.out.PaymentRepository;
import com.teambind.payment.application.port.out.TossPaymentClient;
import com.teambind.payment.domain.Money;
import com.teambind.payment.domain.Payment;
import com.teambind.payment.domain.PaymentMethod;
import com.teambind.payment.domain.PaymentStatus;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @InjectMocks
    private PaymentConfirmService paymentConfirmService;

    private Payment payment;
    private TossPaymentConfirmResponse tossResponse;

    @BeforeEach
    void setUp() {
        payment = Payment.prepare("reservation-123", Money.of(100000L), LocalDateTime.now().plusDays(7));

        tossResponse = new TossPaymentConfirmResponse(
                "payment-key-123",
                "order-123",
                "NORMAL",
                "transaction-123",
                100000L,
                "CARD",
                "DONE",
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("결제 승인 성공 - 카드 결제")
    void confirmPayment_success_card() {
        // given
        String paymentId = payment.getPaymentId();
        String orderId = "order-123";
        String paymentKey = "payment-key-123";
        Long amount = 100000L;

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(tossPaymentClient.confirmPayment(any(TossPaymentConfirmRequest.class))).willReturn(tossResponse);
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Payment result = paymentConfirmService.confirmPayment(paymentId, orderId, paymentKey, amount);

        // then
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getPaymentKey()).isEqualTo(paymentKey);
        assertThat(result.getTransactionId()).isEqualTo("transaction-123");
        assertThat(result.getMethod()).isEqualTo(PaymentMethod.CARD);

        verify(paymentRepository).findById(paymentId);
        verify(tossPaymentClient).confirmPayment(any(TossPaymentConfirmRequest.class));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("결제 승인 성공 - 간편결제")
    void confirmPayment_success_easyPay() {
        // given
        String paymentId = payment.getPaymentId();
        TossPaymentConfirmResponse easyPayResponse = new TossPaymentConfirmResponse(
                "payment-key-123",
                "order-123",
                "NORMAL",
                "transaction-123",
                100000L,
                "EASY_PAY",
                "DONE",
                LocalDateTime.now()
        );

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(tossPaymentClient.confirmPayment(any(TossPaymentConfirmRequest.class))).willReturn(easyPayResponse);
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Payment result = paymentConfirmService.confirmPayment(paymentId, "order-123", "payment-key-123", 100000L);

        // then
        assertThat(result.getMethod()).isEqualTo(PaymentMethod.EASY_PAY);
    }

    @Test
    @DisplayName("결제 승인 성공 - 가상계좌")
    void confirmPayment_success_virtualAccount() {
        // given
        String paymentId = payment.getPaymentId();
        TossPaymentConfirmResponse virtualAccountResponse = new TossPaymentConfirmResponse(
                "payment-key-123",
                "order-123",
                "NORMAL",
                "transaction-123",
                100000L,
                "VIRTUAL_ACCOUNT",
                "DONE",
                LocalDateTime.now()
        );

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(tossPaymentClient.confirmPayment(any(TossPaymentConfirmRequest.class))).willReturn(virtualAccountResponse);
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Payment result = paymentConfirmService.confirmPayment(paymentId, "order-123", "payment-key-123", 100000L);

        // then
        assertThat(result.getMethod()).isEqualTo(PaymentMethod.VIRTUAL_ACCOUNT);
    }

    @Test
    @DisplayName("결제 승인 실패 - 결제 정보를 찾을 수 없음")
    void confirmPayment_fail_paymentNotFound() {
        // given
        String paymentId = "non-existent-id";
        given(paymentRepository.findById(paymentId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentConfirmService.confirmPayment(
                paymentId, "order-123", "payment-key-123", 100000L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제 정보를 찾을 수 없습니다");

        verify(paymentRepository).findById(paymentId);
        verify(tossPaymentClient, never()).confirmPayment(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("결제 승인 실패 - 금액 불일치")
    void confirmPayment_fail_amountMismatch() {
        // given
        String paymentId = payment.getPaymentId();
        Long wrongAmount = 200000L; // 원래 금액과 다름

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> paymentConfirmService.confirmPayment(
                paymentId, "order-123", "payment-key-123", wrongAmount
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액이 일치하지 않습니다");

        verify(paymentRepository).findById(paymentId);
        verify(tossPaymentClient, never()).confirmPayment(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("결제 승인 실패 - 토스 API 호출 실패")
    void confirmPayment_fail_tossApiError() {
        // given
        String paymentId = payment.getPaymentId();
        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(tossPaymentClient.confirmPayment(any(TossPaymentConfirmRequest.class)))
                .willThrow(new RuntimeException("토스 API 오류"));

        // when & then
        assertThatThrownBy(() -> paymentConfirmService.confirmPayment(
                paymentId, "order-123", "payment-key-123", 100000L
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("토스 API 오류");

        verify(paymentRepository).findById(paymentId);
        verify(tossPaymentClient).confirmPayment(any(TossPaymentConfirmRequest.class));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("결제 수단 매핑 - 한글 입력")
    void confirmPayment_methodMapping_korean() {
        // given
        String paymentId = payment.getPaymentId();
        TossPaymentConfirmResponse koreanResponse = new TossPaymentConfirmResponse(
                "payment-key-123",
                "order-123",
                "NORMAL",
                "transaction-123",
                100000L,
                "카드",
                "DONE",
                LocalDateTime.now()
        );

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(tossPaymentClient.confirmPayment(any(TossPaymentConfirmRequest.class))).willReturn(koreanResponse);
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Payment result = paymentConfirmService.confirmPayment(paymentId, "order-123", "payment-key-123", 100000L);

        // then
        assertThat(result.getMethod()).isEqualTo(PaymentMethod.CARD);
    }

    @Test
    @DisplayName("결제 수단 매핑 - 알 수 없는 결제 수단은 CARD로 기본 처리")
    void confirmPayment_methodMapping_unknown() {
        // given
        String paymentId = payment.getPaymentId();
        TossPaymentConfirmResponse unknownResponse = new TossPaymentConfirmResponse(
                "payment-key-123",
                "order-123",
                "NORMAL",
                "transaction-123",
                100000L,
                "UNKNOWN_METHOD",
                "DONE",
                LocalDateTime.now()
        );

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
        given(tossPaymentClient.confirmPayment(any(TossPaymentConfirmRequest.class))).willReturn(unknownResponse);
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        Payment result = paymentConfirmService.confirmPayment(paymentId, "order-123", "payment-key-123", 100000L);

        // then
        assertThat(result.getMethod()).isEqualTo(PaymentMethod.CARD); // 기본값
    }
}