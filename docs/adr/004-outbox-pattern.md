# ADR 004: Outbox Pattern 채택

**날짜**: 2025-11-21  
**상태**: 승인됨  
**결정자**: TeamBind

---

## 문제 정의

결제 완료 후 예약 서비스에 이벤트 발행 시 원자성이 보장되지 않음.

```java
// ❌ 문제: DB 커밋과 Kafka 발행이 별개의 트랜잭션
@Transactional
public void processPayment() {
    payment.complete();
    paymentRepository.save(payment);  // DB 커밋
    kafkaTemplate.send("payment-events", event);  // Kafka 발행 실패 가능!
}
```

**리스크:**

- DB는 저장되었는데 이벤트 미발행 → 예약 확정 안 됨
- 금융 도메인에서 치명적

---

## 결정 사항

**Outbox Pattern을 채택합니다.**

### 구현

```java
// ✅ 해결: 같은 트랜잭션에 저장
@Transactional
public void processPayment() {
    payment.complete();
    paymentRepository.save(payment);
    
    // Outbox 테이블에 이벤트 저장
    paymentEventRepository.save(PaymentEvent.completed(payment));
}

// 별도 스케줄러가 비동기로 발행
@Scheduled(fixedDelay = 1000)
@Transactional
public void publishPendingEvents() {
    List<PaymentEvent> pending = eventRepository.findPending();
    
    for (PaymentEvent event : pending) {
        kafkaTemplate.send("payment-events", event.getPayload());
        event.markAsPublished();
        eventRepository.save(event);
    }
}
```

---

## 장점

1. **원자성 보장**: DB 커밋 = 이벤트 저장
2. **At-least-once 전달**: 재시도로 반드시 발행
3. **이벤트 이력 추적**: Outbox 테이블에 모든 이벤트 기록

---

**최종 승인**: 2025-11-21
