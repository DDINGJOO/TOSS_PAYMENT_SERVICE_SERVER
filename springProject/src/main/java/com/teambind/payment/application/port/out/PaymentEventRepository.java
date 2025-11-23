package com.teambind.payment.application.port.out;

import com.teambind.payment.domain.PaymentEvent;

import java.util.List;
import java.util.Optional;

public interface PaymentEventRepository {

    // 이벤트 저장
    PaymentEvent save(PaymentEvent event);

    // 이벤트 ID로 조회
    Optional<PaymentEvent> findById(Long eventId);

    // 발행 대기중인 이벤트 조회 (Outbox 패턴 - 배치 발행용)
    List<PaymentEvent> findPendingEvents(int limit);

    // 재시도 가능한 실패 이벤트 조회
    List<PaymentEvent> findFailedEventsForRetry(int maxRetryCount, int limit);
}
