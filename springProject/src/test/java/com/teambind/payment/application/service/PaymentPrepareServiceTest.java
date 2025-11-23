package com.teambind.payment.application.service;

import com.teambind.payment.application.port.out.PaymentRepository;
import com.teambind.payment.domain.Money;
import com.teambind.payment.domain.Payment;
import com.teambind.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentPrepareService 테스트")
class PaymentPrepareServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentPrepareService paymentPrepareService;

    @Test
    @DisplayName("결제 준비 - 신규 예약")
    void preparePayment_NewReservation_Success() {
        // Given
        String reservationId = "RSV-001";
        Long amount = 50000L;
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(7);

        given(paymentRepository.findByReservationId(reservationId))
                .willReturn(Optional.empty());

        Payment expectedPayment = Payment.prepare(reservationId, Money.of(amount), checkInDate);
        given(paymentRepository.save(any(Payment.class)))
                .willReturn(expectedPayment);

        // When
        Payment result = paymentPrepareService.preparePayment(reservationId, amount, checkInDate);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getReservationId()).isEqualTo(reservationId);
        assertThat(result.getAmount()).isEqualTo(Money.of(amount));
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PREPARED);

        verify(paymentRepository).findByReservationId(reservationId);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("결제 준비 - 이미 처리된 예약 (멱등성)")
    void preparePayment_ExistingReservation_ReturnsExisting() {
        // Given
        String reservationId = "RSV-002";
        Long amount = 50000L;
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(7);

        Payment existingPayment = Payment.prepare(reservationId, Money.of(amount), checkInDate);
        given(paymentRepository.findByReservationId(reservationId))
                .willReturn(Optional.of(existingPayment));

        // When
        Payment result = paymentPrepareService.preparePayment(reservationId, amount, checkInDate);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(existingPayment);
        assertThat(result.getReservationId()).isEqualTo(reservationId);

        verify(paymentRepository).findByReservationId(reservationId);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("결제 준비 - 다양한 금액")
    void preparePayment_VariousAmounts_Success() {
        // Given
        String reservationId = "RSV-003";
        Long amount = 100000L;
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(14);

        given(paymentRepository.findByReservationId(anyString()))
                .willReturn(Optional.empty());

        Payment expectedPayment = Payment.prepare(reservationId, Money.of(amount), checkInDate);
        given(paymentRepository.save(any(Payment.class)))
                .willReturn(expectedPayment);

        // When
        Payment result = paymentPrepareService.preparePayment(reservationId, amount, checkInDate);

        // Then
        assertThat(result.getAmount()).isEqualTo(Money.of(100000));
    }
}