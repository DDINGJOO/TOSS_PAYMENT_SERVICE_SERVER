package com.teambind.payment.adapter.out.persistence;

import com.teambind.payment.application.port.out.PaymentEventRepository;
import com.teambind.payment.domain.EventStatus;
import com.teambind.payment.domain.PaymentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentEventRepositoryAdapter implements PaymentEventRepository {

    private final PaymentEventJpaRepository jpaRepository;

    @Override
    public PaymentEvent save(PaymentEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public Optional<PaymentEvent> findById(Long eventId) {
        return jpaRepository.findById(eventId);
    }

    @Override
    public List<PaymentEvent> findPendingEvents(int limit) {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(
                EventStatus.PENDING,
                PageRequest.of(0, limit)
        );
    }

    @Override
    public List<PaymentEvent> findFailedEventsForRetry(int maxRetryCount, int limit) {
        return jpaRepository.findFailedEventsForRetry(
                maxRetryCount,
                PageRequest.of(0, limit)
        );
    }
}