package com.teambind.payment.adapter.out.persistence;

import com.teambind.payment.application.port.out.RefundRepository;
import com.teambind.payment.domain.Refund;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefundRepositoryAdapter implements RefundRepository {

    private final RefundJpaRepository jpaRepository;

    @Override
    public Refund save(Refund refund) {
        return jpaRepository.save(refund);
    }

    @Override
    public Optional<Refund> findById(String refundId) {
        return jpaRepository.findById(refundId);
    }

    @Override
    public List<Refund> findByPaymentId(String paymentId) {
        return jpaRepository.findByPaymentId(paymentId);
    }
}