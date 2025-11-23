package com.teambind.payment.adapter.out.persistence;

import com.teambind.payment.domain.EventStatus;
import com.teambind.payment.domain.PaymentEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentEventJpaRepository extends JpaRepository<PaymentEvent, Long> {

    // 발행 대기중인 이벤트 조회 (생성 시각 오름차순)
    List<PaymentEvent> findByStatusOrderByCreatedAtAsc(EventStatus status, Pageable pageable);

    // 재시도 가능한 실패 이벤트 조회 (재시도 횟수가 최대값 미만)
    @Query("SELECT e FROM PaymentEvent e WHERE e.status = 'FAILED' AND e.retryCount < :maxRetryCount ORDER BY e.createdAt ASC")
    List<PaymentEvent> findFailedEventsForRetry(@Param("maxRetryCount") int maxRetryCount, Pageable pageable);
}