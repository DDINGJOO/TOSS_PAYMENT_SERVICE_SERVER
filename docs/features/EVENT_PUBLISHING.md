# 이벤트 발행 (Event Publishing)

> Version: v0.0.1-SNAPSHOT | Java 21 | Spring Boot 3.5.7

## 개요

Toss Payment Service는 **Outbox Pattern**을 사용하여 안정적인 이벤트 발행을 보장합니다. 비즈니스 트랜잭션과 이벤트 발행의 원자성을 유지하며, Kafka를 통해 다른 마이크로서비스와
통신합니다.

## Outbox Pattern

### 개념

Outbox Pattern은 이벤트를 외부 메시징 시스템에 직접 발행하지 않고, 비즈니스 트랜잭션 내에서 데이터베이스 테이블에 먼저 저장한 후, 별도의 스케줄러가 주기적으로 이벤트를 조회하여 발행하는 패턴입니다.

### 왜 Outbox Pattern인가?

**문제 상황**:

```java
@Transactional
public Payment confirmPayment(...) {
    // 1. DB 저장
    Payment payment = paymentRepository.save(payment);

    // 2. Kafka 발행
    kafkaTemplate.send("payment-completed", event);  // ⚠️ 발행 실패 시?

    return payment;
}
```

**문제점**:

- DB 저장은 성공했지만 Kafka 발행이 실패하면?
- Kafka 발행은 성공했지만 DB 저장이 롤백되면?
- **데이터 불일치 발생**

**해결책 (Outbox Pattern)**:

```java
@Transactional
public Payment confirmPayment(...) {
    // 1. DB 저장 (Payment + PaymentEvent)
    Payment payment = paymentRepository.save(payment);
    PaymentEvent event = PaymentEvent.create(...);
    paymentEventRepository.save(event);  // ✅ 같은 트랜잭션

    return payment;
}

// 별도 스케줄러
@Scheduled(fixedDelay = 1000)
public void publishPendingEvents() {
    List<PaymentEvent> events = paymentEventRepository.findPendingEvents();
    events.forEach(event -> {
        kafkaTemplate.send(event.getEventType().getTopic(), event.getPayload());
        event.markAsPublished();
        paymentEventRepository.save(event);
    });
}
```

---

## 아키텍처

### 전체 플로우

```
┌─────────────────────────────────────────────────────────────────┐
│                   Business Transaction                          │
│                                                                 │
│  Service Layer                                                  │
│     │                                                           │
│     ├─> PaymentRepository.save(payment)                        │
│     │                                                           │
│     ├─> PaymentEventPublisher.publishEvent(event)              │
│     │      │                                                    │
│     │      ├─> PaymentEvent.create(...)                        │
│     │      └─> PaymentEventRepository.save(event)  ← DB 저장   │
│     │                                                           │
│     └─> return Payment                                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ COMMIT
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                  Outbox Event Scheduler                         │
│                                                                 │
│  @Scheduled(fixedDelay = 1000)                                 │
│     │                                                           │
│     ├─> findPendingEvents() ← status = PENDING                 │
│     │                                                           │
│     ├─> forEach event:                                         │
│     │      ├─> kafkaTemplate.send(event)                       │
│     │      ├─> if success:                                     │
│     │      │      └─> event.markAsPublished()                  │
│     │      │      └─> save(event)                              │
│     │      └─> if failure:                                     │
│     │             └─> event.markAsFailed(errorMessage)         │
│     │             └─> save(event)                              │
│     │                                                           │
│     └─> Log results                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ↓
                          Kafka Broker
```

---

## PaymentEvent Entity

### 데이터베이스 스키마

```sql
CREATE TABLE payment_events (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id VARCHAR(50) NOT NULL,        -- Payment ID 또는 Refund ID
    event_type VARCHAR(50) NOT NULL,          -- PAYMENT_COMPLETED, PAYMENT_CANCELLED, REFUND_COMPLETED
    payload TEXT NOT NULL,                    -- JSON 형식의 이벤트 데이터
    status VARCHAR(20) NOT NULL,              -- PENDING, PUBLISHED, FAILED
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT
);

CREATE INDEX idx_payment_events_status ON payment_events(status);
CREATE INDEX idx_payment_events_created_at ON payment_events(created_at);
```

### Entity 클래스

```java
@Entity
@Table(name = "payment_events")
public class PaymentEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    private String aggregateId;         // Payment ID or Refund ID

    @Enumerated(EnumType.STRING)
    private EventType eventType;        // PAYMENT_COMPLETED, PAYMENT_CANCELLED, REFUND_COMPLETED

    private String payload;             // JSON

    @Enumerated(EnumType.STRING)
    private EventStatus status;         // PENDING, PUBLISHED, FAILED

    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private Integer retryCount;
    private String errorMessage;
}
```

### EventType

```java
public enum EventType {
    PAYMENT_COMPLETED("payment-completed"),
    PAYMENT_CANCELLED("payment-cancelled"),
    REFUND_COMPLETED("refund-completed");

    private final String topic;

    EventType(String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }
}
```

### EventStatus

```java
public enum EventStatus {
    PENDING,        // 발행 대기 중
    PUBLISHED,      // 발행 완료
    FAILED          // 발행 실패
}
```

---

## PaymentEventPublisher

### 인터페이스

```java
public interface PaymentEventPublisher {
    void publishPaymentCompletedEvent(PaymentCompletedEvent event);
    void publishPaymentCancelledEvent(PaymentCancelledEvent event);
    void publishRefundCompletedEvent(RefundCompletedEvent event);
}
```

### 구현 클래스

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final PaymentEventRepository paymentEventRepository;
    private final JsonUtil jsonUtil;

    public void publishPaymentCompletedEvent(PaymentCompletedEvent event) {
        log.info("결제 완료 이벤트 저장 - paymentId: {}", event.paymentId());

        PaymentEvent paymentEvent = PaymentEvent.create(
                event.paymentId(),
                EventType.PAYMENT_COMPLETED,
                jsonUtil.toJson(event)
        );

        paymentEventRepository.save(paymentEvent);
        log.info("PaymentEvent 저장 완료 - eventId: {}, aggregateId: {}",
                paymentEvent.getEventId(), paymentEvent.getAggregateId());
    }

    public void publishPaymentCancelledEvent(PaymentCancelledEvent event) {
        log.info("결제 취소 이벤트 저장 - paymentId: {}", event.paymentId());

        PaymentEvent paymentEvent = PaymentEvent.create(
                event.paymentId(),
                EventType.PAYMENT_CANCELLED,
                jsonUtil.toJson(event)
        );

        paymentEventRepository.save(paymentEvent);
    }

    public void publishRefundCompletedEvent(RefundCompletedEvent event) {
        log.info("환불 완료 이벤트 저장 - refundId: {}", event.refundId());

        PaymentEvent paymentEvent = PaymentEvent.create(
                event.refundId(),
                EventType.REFUND_COMPLETED,
                jsonUtil.toJson(event)
        );

        paymentEventRepository.save(paymentEvent);
    }
}
```

---

## OutboxEventScheduler

### 스케줄러 구현

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventScheduler {

    private static final int MAX_RETRY_COUNT = 5;

    private final PaymentEventRepository paymentEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)  // 1초마다 실행
    @Transactional
    public void publishPendingEvents() {
        List<PaymentEvent> pendingEvents = paymentEventRepository.findPendingEvents();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("발행 대기 중인 이벤트 {} 개 발견", pendingEvents.size());

        for (PaymentEvent event : pendingEvents) {
            try {
                // Kafka 발행
                CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                        event.getEventType().getTopic(),
                        event.getAggregateId(),
                        event.getPayload()
                );

                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        // 발행 성공
                        event.markAsPublished();
                        paymentEventRepository.save(event);
                        log.info("이벤트 발행 성공 - eventId: {}, topic: {}",
                                event.getEventId(), event.getEventType().getTopic());
                    } else {
                        // 발행 실패
                        event.markAsFailed(ex.getMessage());
                        paymentEventRepository.save(event);
                        log.error("이벤트 발행 실패 - eventId: {}, error: {}",
                                event.getEventId(), ex.getMessage(), ex);
                    }
                });

            } catch (Exception e) {
                // 예외 발생
                event.markAsFailed(e.getMessage());
                paymentEventRepository.save(event);
                log.error("이벤트 발행 중 예외 발생 - eventId: {}, error: {}",
                        event.getEventId(), e.getMessage(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 60000)  // 1분마다 실행
    @Transactional
    public void retryFailedEvents() {
        List<PaymentEvent> failedEvents = paymentEventRepository.findFailedEventsForRetry(MAX_RETRY_COUNT);

        if (failedEvents.isEmpty()) {
            return;
        }

        log.info("재시도 대상 실패 이벤트 {} 개 발견", failedEvents.size());

        for (PaymentEvent event : failedEvents) {
            if (event.canRetry(MAX_RETRY_COUNT)) {
                event.resetForRetry();
                paymentEventRepository.save(event);
                log.info("이벤트 재시도 대기 상태로 변경 - eventId: {}, retryCount: {}",
                        event.getEventId(), event.getRetryCount());
            } else {
                log.warn("최대 재시도 횟수 초과 - eventId: {}, retryCount: {}",
                        event.getEventId(), event.getRetryCount());
            }
        }
    }
}
```

### Repository 쿼리

```java
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    @Query("SELECT e FROM PaymentEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<PaymentEvent> findPendingEvents();

    @Query("SELECT e FROM PaymentEvent e WHERE e.status = 'FAILED' AND e.retryCount < :maxRetryCount ORDER BY e.createdAt ASC")
    List<PaymentEvent> findFailedEventsForRetry(@Param("maxRetryCount") int maxRetryCount);
}
```

---

## 이벤트 타입별 페이로드

### PaymentCompletedEvent

```json
{
  "paymentId": "PAY-A1B2C3D4",
  "reservationId": "reservation-123",
  "amount": 100000,
  "currency": "KRW",
  "method": "CARD",
  "orderId": "order-123",
  "paymentKey": "toss-payment-key-abc123",
  "transactionId": "transaction-xyz789",
  "paidAt": "2025-11-23T10:35:00"
}
```

**Topic**: `payment-completed`

**Consumers**:

- Reservation Service: 예약 상태 업데이트
- Notification Service: 결제 완료 알림 발송
- Analytics Service: 결제 데이터 수집

---

### PaymentCancelledEvent

```json
{
  "paymentId": "PAY-A1B2C3D4",
  "reservationId": "reservation-123",
  "cancelledAt": "2025-11-23T11:00:00"
}
```

**Topic**: `payment-cancelled`

**Consumers**:

- Reservation Service: 예약 상태 취소로 변경
- Notification Service: 결제 취소 알림 발송

---

### RefundCompletedEvent

```json
{
  "refundId": "REF-X9Y8Z7W6",
  "paymentId": "PAY-A1B2C3D4",
  "reservationId": "reservation-123",
  "originalAmount": 100000,
  "refundAmount": 50000,
  "currency": "KRW",
  "transactionId": "refund-transaction-123",
  "completedAt": "2025-11-23T11:00:05"
}
```

**Topic**: `refund-completed`

**Consumers**:

- Reservation Service: 예약 상태 환불로 변경
- Notification Service: 환불 완료 알림 발송
- Accounting Service: 환불 회계 처리

---

## 트랜잭션 보장

### 원자성 보장

```java
@Transactional
public Payment confirmPayment(String paymentId, String orderId, String paymentKey, Long amount) {
    // 1. Payment 업데이트
    Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> PaymentException.notFound(paymentId));
    payment.complete(orderId, paymentKey, transactionId, method);
    Payment savedPayment = paymentRepository.save(payment);

    // 2. PaymentEvent 저장 (같은 트랜잭션)
    PaymentCompletedEvent event = PaymentCompletedEvent.from(savedPayment);
    paymentEventPublisher.publishPaymentCompletedEvent(event);

    // 3. 둘 다 성공해야 COMMIT, 하나라도 실패하면 ROLLBACK
    return savedPayment;
}
```

**시나리오 1: 정상 플로우**

```
Payment 저장 성공 → PaymentEvent 저장 성공 → COMMIT
```

**시나리오 2: PaymentEvent 저장 실패**

```
Payment 저장 성공 → PaymentEvent 저장 실패 → ROLLBACK (Payment도 롤백)
```

**시나리오 3: Kafka 발행 실패**

```
Payment 저장 성공 → PaymentEvent 저장 성공 (status=PENDING) → COMMIT
↓
OutboxEventScheduler가 발행 시도
↓
Kafka 발행 실패 → event.markAsFailed() → 재시도 대기
```

---

## 재시도 로직

### 재시도 전략

1. **즉시 재시도**: OutboxEventScheduler가 1초마다 PENDING 이벤트 조회 및 발행
2. **실패 재시도**: 1분마다 FAILED 이벤트 중 재시도 가능한 이벤트 조회
3. **최대 재시도 횟수**: 5회
4. **재시도 초과 시**: 수동 개입 필요 (모니터링 알림)

### 재시도 플로우

```
PENDING ─┬─> Kafka 발행 성공 ─> PUBLISHED (종료)
         │
         └─> Kafka 발행 실패 ─> FAILED (retryCount++)
                                  │
                                  ├─> retryCount < 5 ─> resetForRetry() ─> PENDING
                                  │
                                  └─> retryCount >= 5 ─> 로그 기록 (수동 처리 필요)
```

### 코드 예시

```java
public boolean canRetry(int maxRetryCount) {
    return this.retryCount < maxRetryCount && this.status == EventStatus.FAILED;
}

public void resetForRetry() {
    if (this.status == EventStatus.PUBLISHED) {
        throw new IllegalStateException("이미 발행된 이벤트는 재시도할 수 없습니다");
    }

    this.status = EventStatus.PENDING;
    this.errorMessage = null;
}
```

---

## 모니터링

### 모니터링 지표

1. **PENDING 이벤트 수**
	- 정상: 0~10개
	- 주의: 10~100개 (발행 지연)
	- 위험: 100개 이상 (Kafka 장애 가능성)

2. **FAILED 이벤트 수**
	- 정상: 0개
	- 주의: 1~10개 (일시적 오류)
	- 위험: 10개 이상 (Kafka 장애 또는 설정 오류)

3. **평균 발행 시간**
	- 정상: 이벤트 생성 후 1~2초 이내 발행
	- 주의: 2~10초
	- 위험: 10초 이상

4. **재시도 횟수**
	- 정상: 0회
	- 주의: 1~3회
	- 위험: 4~5회 (수동 개입 고려)

### 로그 예시

```
2025-11-23 10:35:01 [INFO ] 결제 완료 이벤트 저장 - paymentId: PAY-A1B2C3D4
2025-11-23 10:35:01 [INFO ] PaymentEvent 저장 완료 - eventId: 1001, aggregateId: PAY-A1B2C3D4
2025-11-23 10:35:02 [INFO ] 발행 대기 중인 이벤트 1 개 발견
2025-11-23 10:35:02 [INFO ] 이벤트 발행 성공 - eventId: 1001, topic: payment-completed
```

### 실패 로그 예시

```
2025-11-23 10:35:02 [ERROR] 이벤트 발행 실패 - eventId: 1002, error: Connection refused
2025-11-23 10:36:02 [INFO ] 재시도 대상 실패 이벤트 1 개 발견
2025-11-23 10:36:02 [INFO ] 이벤트 재시도 대기 상태로 변경 - eventId: 1002, retryCount: 1
```

---

## Kafka 설정

### Producer 설정

```java
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // 안정성 설정
        props.put(ProducerConfig.ACKS_CONFIG, "all");  // 모든 복제본 확인
        props.put(ProducerConfig.RETRIES_CONFIG, 3);   // 재시도 3회
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);  // 순서 보장

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

---

## 테스트

### OutboxEventScheduler 테스트

```java
@Test
@DisplayName("PENDING 이벤트 발행 성공")
void publishPendingEvents_success() {
    // given
    PaymentEvent event = PaymentEvent.create(
        "PAY-A1B2C3D4",
        EventType.PAYMENT_COMPLETED,
        "{\"paymentId\":\"PAY-A1B2C3D4\"}"
    );
    paymentEventRepository.save(event);

    // when
    outboxEventScheduler.publishPendingEvents();

    // then
    PaymentEvent published = paymentEventRepository.findById(event.getEventId()).get();
    assertThat(published.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    assertThat(published.getPublishedAt()).isNotNull();
}
```

---

## 장애 시나리오 및 대응

### 시나리오 1: Kafka Broker 장애

**상황**: Kafka Broker가 다운되어 이벤트 발행 불가

**대응**:

1. PaymentEvent는 PENDING 상태로 DB에 누적
2. OutboxEventScheduler가 계속 재시도
3. Kafka 복구 후 자동으로 발행 재개

**데이터 손실**: 없음 (DB에 안전하게 저장됨)

---

### 시나리오 2: Database 장애

**상황**: DB 다운으로 PaymentEvent 저장 실패

**대응**:

1. Payment 저장도 함께 롤백
2. 클라이언트에게 500 에러 반환
3. 클라이언트가 결제 승인 재시도

**데이터 손실**: 없음 (트랜잭션 롤백)

---

### 시나리오 3: OutboxEventScheduler 장애

**상황**: 스케줄러가 비정상 종료

**대응**:

1. PaymentEvent는 PENDING 상태로 DB에 누적
2. 스케줄러 재시작 후 자동으로 발행 재개

**데이터 손실**: 없음 (발행만 지연)

---

## 관련 문서

- [Domain Model](../implementation/DOMAIN_MODEL.md)
- [Payment Flow](PAYMENT_FLOW.md)
- [Refund Flow](REFUND_FLOW.md)
- [ADR-004: Outbox Pattern](../adr/004-outbox-pattern.md)

---

**최종 업데이트**: 2025-11-23
**작성자**: TeamBind
**PR**: #31
