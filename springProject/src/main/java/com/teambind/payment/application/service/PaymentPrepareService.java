package com.teambind.payment.application.service;

import com.teambind.payment.application.port.out.PaymentRepository;
import com.teambind.payment.domain.Money;
import com.teambind.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentPrepareService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment preparePayment(String reservationId, Long amount, LocalDateTime checkInDate) {
        log.info("결제 준비 시작 - reservationId: {}, amount: {}", reservationId, amount);

        // 멱등성 체크 - 이미 처리된 예약인지 확인
        return paymentRepository.findByReservationId(reservationId)
                .map(existingPayment -> {
                    log.info("이미 처리된 예약입니다 - paymentId: {}", existingPayment.getPaymentId());
                    return existingPayment;
                })
                .orElseGet(() -> {
                    // 새로운 결제 준비
                    Payment payment = Payment.prepare(
                            reservationId,
                            Money.of(amount),
                            checkInDate
                    );

                    Payment savedPayment = paymentRepository.save(payment);
                    log.info("결제 준비 완료 - paymentId: {}, status: {}",
                            savedPayment.getPaymentId(), savedPayment.getStatus());

                    return savedPayment;
                });
    }
}