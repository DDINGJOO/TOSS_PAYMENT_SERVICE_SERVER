package com.teambind.payment.adapter.in.kafka;

import com.teambind.payment.adapter.in.kafka.dto.ReservationConfirmedEvent;
import com.teambind.payment.application.service.PaymentPrepareService;
import com.teambind.payment.domain.Money;
import com.teambind.payment.domain.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventConsumer 테스트")
class PaymentEventConsumerTest {

    @Mock
    private PaymentPrepareService paymentPrepareService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private PaymentEventConsumer paymentEventConsumer;

    @Test
    @DisplayName("예약 확정 이벤트 처리 - 성공")
    void handleReservationConfirmed_Success() {
        // Given
        String reservationId = "RSV-001";
        Long amount = 50000L;
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(7);

        ReservationConfirmedEvent event = new ReservationConfirmedEvent(
                reservationId,
                amount,
                checkInDate
        );

        Payment payment = Payment.prepare(reservationId, Money.of(amount), checkInDate);
        given(paymentPrepareService.preparePayment(anyString(), anyLong(), any(LocalDateTime.class)))
                .willReturn(payment);

        // When
        paymentEventConsumer.handleReservationConfirmed(
                event,
                "reservation-confirmed",
                0,
                0L,
                acknowledgment
        );

        // Then
        verify(paymentPrepareService).preparePayment(reservationId, amount, checkInDate);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("예약 확정 이벤트 처리 - 서비스 실패 시 예외 발생")
    void handleReservationConfirmed_ServiceFailure_ThrowsException() {
        // Given
        ReservationConfirmedEvent event = new ReservationConfirmedEvent(
                "RSV-002",
                50000L,
                LocalDateTime.now().plusDays(7)
        );

        given(paymentPrepareService.preparePayment(anyString(), anyLong(), any(LocalDateTime.class)))
                .willThrow(new RuntimeException("Database error"));

        // When & Then
        assertThatThrownBy(() -> paymentEventConsumer.handleReservationConfirmed(
                event,
                "reservation-confirmed",
                0,
                0L,
                acknowledgment
        )).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Database error");

        verify(paymentPrepareService).preparePayment(anyString(), anyLong(), any(LocalDateTime.class));
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("예약 확정 이벤트 처리 - 멱등성 확인")
    void handleReservationConfirmed_Idempotency_Success() {
        // Given
        String reservationId = "RSV-003";
        Long amount = 100000L;
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(14);

        ReservationConfirmedEvent event = new ReservationConfirmedEvent(
                reservationId,
                amount,
                checkInDate
        );

        Payment existingPayment = Payment.prepare(reservationId, Money.of(amount), checkInDate);
        given(paymentPrepareService.preparePayment(anyString(), anyLong(), any(LocalDateTime.class)))
                .willReturn(existingPayment);

        // When - 동일한 이벤트를 두 번 처리
        paymentEventConsumer.handleReservationConfirmed(event, "topic", 0, 0L, acknowledgment);
        paymentEventConsumer.handleReservationConfirmed(event, "topic", 0, 1L, acknowledgment);

        // Then
        verify(paymentPrepareService, times(2)).preparePayment(reservationId, amount, checkInDate);
        verify(acknowledgment, times(2)).acknowledge();
    }
}