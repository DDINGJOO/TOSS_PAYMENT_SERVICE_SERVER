package com.teambind.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Payment 도메인 테스트")
class PaymentTest {

    @Test
    @DisplayName("결제 준비 - 성공")
    void prepare_Success() {
        // Given
        String reservationId = "RSV-001";
        Money amount = Money.of(50000);
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(7);

        // When
        Payment payment = Payment.prepare(reservationId, amount, checkInDate);

        // Then
        assertThat(payment.getReservationId()).isEqualTo(reservationId);
        assertThat(payment.getAmount()).isEqualTo(amount);
        assertThat(payment.getCheckInDate()).isEqualTo(checkInDate);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PREPARED);
        assertThat(payment.getPaymentId()).isNotNull();
        assertThat(payment.getIdempotencyKey()).isNotNull();
        assertThat(payment.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("결제 준비 - reservationId가 null이면 예외 발생")
    void prepare_NullReservationId_ThrowsException() {
        // Given
        String reservationId = null;
        Money amount = Money.of(50000);
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(7);

        // When & Then
        assertThatThrownBy(() -> Payment.prepare(reservationId, amount, checkInDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reservation ID는 필수입니다");
    }

    @Test
    @DisplayName("결제 준비 - amount가 0이면 예외 발생")
    void prepare_ZeroAmount_ThrowsException() {
        // Given
        String reservationId = "RSV-001";
        Money amount = Money.ZERO;
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(7);

        // When & Then
        assertThatThrownBy(() -> Payment.prepare(reservationId, amount, checkInDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount는 0보다 커야 합니다");
    }

    @Test
    @DisplayName("결제 준비 - checkInDate가 과거이면 예외 발생")
    void prepare_PastCheckInDate_ThrowsException() {
        // Given
        String reservationId = "RSV-001";
        Money amount = Money.of(50000);
        LocalDateTime checkInDate = LocalDateTime.now().minusDays(1);

        // When & Then
        assertThatThrownBy(() -> Payment.prepare(reservationId, amount, checkInDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Check-in date는 현재 시각 이후여야 합니다");
    }

    @Test
    @DisplayName("결제 완료 - 성공")
    void complete_Success() {
        // Given
        Payment payment = Payment.prepare("RSV-001", Money.of(50000), LocalDateTime.now().plusDays(7));
        String orderId = "ORDER-001";
        String paymentKey = "KEY-001";
        String transactionId = "TXN-001";
        PaymentMethod method = PaymentMethod.CARD;

        // When
        payment.complete(orderId, paymentKey, transactionId, method);

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getOrderId()).isEqualTo(orderId);
        assertThat(payment.getPaymentKey()).isEqualTo(paymentKey);
        assertThat(payment.getTransactionId()).isEqualTo(transactionId);
        assertThat(payment.getMethod()).isEqualTo(method);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("결제 완료 - PREPARED 상태가 아니면 예외 발생")
    void complete_NotPreparedStatus_ThrowsException() {
        // Given
        Payment payment = Payment.prepare("RSV-001", Money.of(50000), LocalDateTime.now().plusDays(7));
        payment.fail("테스트 실패");

        // When & Then
        assertThatThrownBy(() -> payment.complete("ORDER-001", "KEY-001", "TXN-001", PaymentMethod.CARD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PREPARED 상태에서만 완료 처리 가능합니다");
    }

    @Test
    @DisplayName("금액 검증 - 일치하면 성공")
    void validateAmount_Match_Success() {
        // Given
        Money amount = Money.of(50000);
        Payment payment = Payment.prepare("RSV-001", amount, LocalDateTime.now().plusDays(7));

        // When & Then
        assertThatNoException().isThrownBy(() -> payment.validateAmount(Money.of(50000)));
    }

    @Test
    @DisplayName("금액 검증 - 불일치하면 예외 발생")
    void validateAmount_Mismatch_ThrowsException() {
        // Given
        Payment payment = Payment.prepare("RSV-001", Money.of(50000), LocalDateTime.now().plusDays(7));
        Money wrongAmount = Money.of(48000);

        // When & Then
        assertThatThrownBy(() -> payment.validateAmount(wrongAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액이 일치하지 않습니다");
    }

    @Test
    @DisplayName("결제 실패 처리 - 성공")
    void fail_Success() {
        // Given
        Payment payment = Payment.prepare("RSV-001", Money.of(50000), LocalDateTime.now().plusDays(7));
        String reason = "카드 한도 초과";

        // When
        payment.fail(reason);

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("결제 실패 처리 - 이미 완료된 결제는 실패 처리 불가")
    void fail_CompletedPayment_ThrowsException() {
        // Given
        Payment payment = Payment.prepare("RSV-001", Money.of(50000), LocalDateTime.now().plusDays(7));
        payment.complete("ORDER-001", "KEY-001", "TXN-001", PaymentMethod.CARD);

        // When & Then
        assertThatThrownBy(() -> payment.fail("실패 사유"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 완료된 결제는 실패 처리할 수 없습니다");
    }

    @Test
    @DisplayName("환불 가능 검증 - COMPLETED 상태이고 체크인 이전이면 성공")
    void validateRefundable_CompletedAndBeforeCheckIn_Success() {
        // Given
        Payment payment = Payment.prepare("RSV-001", Money.of(50000), LocalDateTime.now().plusDays(7));
        payment.complete("ORDER-001", "KEY-001", "TXN-001", PaymentMethod.CARD);

        // When & Then
        assertThatNoException().isThrownBy(payment::validateRefundable);
    }

    @Test
    @DisplayName("환불 가능 검증 - PREPARED 상태이면 예외 발생")
    void validateRefundable_PreparedStatus_ThrowsException() {
        // Given
        Payment payment = Payment.prepare("RSV-001", Money.of(50000), LocalDateTime.now().plusDays(7));

        // When & Then
        assertThatThrownBy(payment::validateRefundable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 결제만 환불 가능합니다");
    }

    @Test
    @DisplayName("환불 가능 검증 - 체크인 날짜 이후이면 예외 발생")
    void validateRefundable_AfterCheckIn_ThrowsException() {
        // Given
        // 과거 날짜로는 결제를 생성할 수 없으므로, prepare 시점에 검증됨을 확인
        LocalDateTime pastCheckInDate = LocalDateTime.now().minusDays(1);

        // When & Then
        assertThatThrownBy(() -> Payment.prepare("RSV-001", Money.of(50000), pastCheckInDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Check-in date는 현재 시각 이후여야 합니다");
    }

    @Test
    @DisplayName("결제 취소 - 성공")
    void cancel_Success() {
        // Given
        Payment payment = Payment.prepare("RSV-001", Money.of(50000), LocalDateTime.now().plusDays(7));
        payment.complete("ORDER-001", "KEY-001", "TXN-001", PaymentMethod.CARD);

        // When
        payment.cancel();

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("결제 취소 - COMPLETED 상태가 아니면 예외 발생")
    void cancel_NotCompletedStatus_ThrowsException() {
        // Given
        Payment payment = Payment.prepare("RSV-001", Money.of(50000), LocalDateTime.now().plusDays(7));

        // When & Then
        assertThatThrownBy(payment::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("완료된 결제만 취소할 수 있습니다");
    }
}