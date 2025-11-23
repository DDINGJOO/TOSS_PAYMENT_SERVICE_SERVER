package com.teambind.payment.application.port.out;

import com.teambind.payment.domain.Payment;

import java.util.Optional;

public interface PaymentRepository {

    // 결제 저장
    Payment save(Payment payment);

    // 결제 ID로 조회
    Optional<Payment> findById(String paymentId);

    // 예약 ID로 조회
    Optional<Payment> findByReservationId(String reservationId);

    // 멱등성 키로 조회 (중복 결제 방지)
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}