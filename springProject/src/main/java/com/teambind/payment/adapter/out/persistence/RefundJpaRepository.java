package com.teambind.payment.adapter.out.persistence;

import com.teambind.payment.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundJpaRepository extends JpaRepository<Refund, String> {

    // 결제 ID로 환불 목록 조회
    List<Refund> findByPaymentId(String paymentId);
}