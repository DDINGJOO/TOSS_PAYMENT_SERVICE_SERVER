package com.teambind.payment.adapter.out.persistence;

import com.teambind.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, String> {

    // 예약 ID로 조회
    Optional<Payment> findByReservationId(String reservationId);

    // 멱등성 키로 조회
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}