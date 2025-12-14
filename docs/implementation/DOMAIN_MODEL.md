# 도메인 모델 설계

> Version: v0.0.1-SNAPSHOT | Java 21 | Spring Boot 3.5.7

## 개요

Toss Payment Service는 도메인 주도 설계(DDD) 원칙을 따라 비즈니스 로직을 도메인 객체에 캡슐화합니다. 엔티티와 값 객체를 명확히 구분하고, 도메인 이벤트를 통해 느슨한 결합을 유지합니다.

## 도메인 모델 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                     Aggregate Root                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Payment                          Refund                   │
│   ├─ paymentId (PK)                ├─ refundId (PK)        │
│   ├─ reservationId                 ├─ paymentId (FK)       │
│   ├─ amount (Money)                ├─ refundAmount (Money) │
│   ├─ method                        ├─ originalAmount       │
│   ├─ status                        ├─ status               │
│   ├─ orderId                       ├─ reason               │
│   ├─ paymentKey                    └─ transactionId        │
│   ├─ transactionId                                         │
│   ├─ checkInDate                                           │
│   └─ idempotencyKey                                        │
│                                                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                     Value Object                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Money                                                     │
│   ├─ value (BigDecimal)                                    │
│   └─ currency (String)                                     │
│                                                             │
│   RefundPolicy                                             │
│   ├─ checkInDate                                           │
│   ├─ refundRequestDate                                     │
│   └─ calculateRefundAmount()                               │
│                                                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                     Domain Event                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   PaymentEvent (Outbox)                                    │
│   ├─ eventId (PK)                                          │
│   ├─ aggregateId                                           │
│   ├─ eventType                                             │
│   ├─ payload (JSON)                                        │
│   ├─ status                                                │
│   └─ retryCount                                            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Aggregate Root

### Payment

**패키지**: `com.teambind.payment.domain.Payment`

**책임**:

- 결제 생명주기 관리 (준비 → 완료 → 취소)
- 결제 금액 및 상태 검증
- 멱등성 보장

**속성**:

```java
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    private String paymentId;           // "PAY-" + UUID (8자리)
    private String reservationId;       // 예약 ID

    @Embedded
    private Money amount;               // 결제 금액 (Value Object)

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;       // CARD, VIRTUAL_ACCOUNT, EASY_PAY

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;       // PREPARED, COMPLETED, FAILED, CANCELLED

    private String orderId;             // 토스 주문 ID
    private String paymentKey;          // 토스 결제 키
    private String transactionId;       // 토스 거래 ID
    private LocalDateTime checkInDate;  // 체크인 날짜 (환불 정책 기준)
    private String idempotencyKey;      // 중복 결제 방지 키

    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private String failureReason;
}
```

**상태 전이**:

```
PREPARED ──complete()──> COMPLETED ──cancel()──> CANCELLED
    │                         │
    └──fail()──> FAILED       └──fail()──> FAILED
```

**주요 메서드**:

#### 결제 준비

```java
public static Payment prepare(
    String reservationId,
    Money amount,
    LocalDateTime checkInDate
)
```

- 새로운 결제 준비
- paymentId, idempotencyKey 자동 생성
- 상태: PREPARED

#### 결제 완료

```java
public void complete(
    String orderId,
    String paymentKey,
    String transactionId,
    PaymentMethod method
)
```

- Toss API 승인 후 호출
- PREPARED 상태에서만 가능
- 상태: COMPLETED

#### 결제 취소

```java
public void cancel()
```

- COMPLETED 상태에서만 가능
- cancelledAt 설정
- 상태: CANCELLED

#### 결제 실패

```java
public void fail(String reason)
```

- COMPLETED 상태 외 모두 가능
- failureReason 설정
- 상태: FAILED

#### 검증 메서드

```java
public void validateAmount(Money requestedAmount)
public void validateRefundable()
```

**비즈니스 규칙**:

1. 결제 ID는 "PAY-" 접두사 + UUID 8자리
2. 멱등성 키는 "IDEM-{reservationId}-{UUID 8자리}"
3. 완료된 결제만 취소 가능
4. 완료된 결제만 환불 가능
5. 체크인 날짜 이후 환불 불가

---

### Refund

**패키지**: `com.teambind.payment.domain.Refund`

**책임**:

- 환불 생명주기 관리 (요청 → 승인 → 완료)
- 환불 금액 검증
- 환불 정책 적용

**속성**:

```java
@Entity
@Table(name = "refunds")
public class Refund {
    @Id
    private String refundId;                    // "REF-" + UUID (8자리)
    private String paymentId;                   // 환불 대상 결제 ID

    @Embedded
    @AttributeOverrides({...})
    private Money refundAmount;                 // 실제 환불 금액

    @Embedded
    @AttributeOverrides({...})
    private Money originalAmount;               // 원래 결제 금액

    @Enumerated(EnumType.STRING)
    private RefundStatus status;                // PENDING, APPROVED, COMPLETED, FAILED

    private String reason;                      // 환불 사유
    private String transactionId;               // 토스 환불 거래 ID

    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime completedAt;
    private String failureReason;
}
```

**상태 전이**:

```
PENDING ──approve()──> APPROVED ──complete()──> COMPLETED
    │                      │
    └──fail()──> FAILED    └──fail()──> FAILED
```

**주요 메서드**:

#### 환불 요청

```java
public static Refund request(
    String paymentId,
    Money originalAmount,
    Money refundAmount,
    String reason
)
```

- 새로운 환불 요청 생성
- refundId 자동 생성
- 상태: PENDING

#### 환불 승인

```java
public void approve()
```

- PENDING 상태에서만 가능
- 상태: APPROVED

#### 환불 완료

```java
public void complete(String transactionId)
```

- APPROVED 상태에서만 가능
- Toss API 환불 완료 후 호출
- 상태: COMPLETED

#### 환불 실패

```java
public void fail(String failureReason)
```

- COMPLETED 외 모두 가능
- 상태: FAILED

**비즈니스 규칙**:

1. 환불 ID는 "REF-" 접두사 + UUID 8자리
2. 환불 금액은 원래 금액을 초과할 수 없음
3. 환불 사유는 필수
4. APPROVED 상태에서만 완료 처리 가능

---

## Value Object

### Money

**패키지**: `com.teambind.payment.domain.Money`

**책임**:

- 금액 표현 및 연산
- 통화 검증
- 불변성 보장

**속성**:

```java
@Embeddable
public class Money {
    @Column(name = "amount")
    private BigDecimal value;           // 원화 금액

    @Column(name = "currency")
    private String currency;            // "KRW" (고정)
}
```

**주요 메서드**:

```java
// 생성
public static Money of(BigDecimal value)
public static Money of(long value)
public static final Money ZERO = new Money(BigDecimal.ZERO, "KRW")

// 연산
public Money multiply(BigDecimal multiplier)
public Money add(Money other)
public Money subtract(Money other)

// 비교
public boolean isGreaterThan(Money other)
public boolean isGreaterThanOrEqual(Money other)
public boolean isLessThan(Money other)
public boolean isZero()
public boolean isPositive()
```

**불변성**:

- 모든 연산은 새로운 Money 객체 반환
- 원본 객체는 변경되지 않음

```java
Money original = Money.of(10000);
Money half = original.multiply(BigDecimal.valueOf(0.5));
// original은 여전히 10000
```

**검증 규칙**:

1. value는 null일 수 없음
2. value는 음수일 수 없음
3. currency는 null 또는 빈 문자열일 수 없음
4. 서로 다른 통화 간 연산 불가

---

### RefundPolicy

**패키지**: `com.teambind.payment.domain.RefundPolicy`

**책임**:

- 체크인 날짜 기준 환불 정책 적용
- 환불 금액 계산
- 환불 가능 여부 판단

**속성**:

```java
public class RefundPolicy {
    private static final int FULL_REFUND_DAYS = 7;      // 100% 환불
    private static final int PARTIAL_REFUND_DAYS = 3;   // 50% 환불
    private static final BigDecimal PARTIAL_REFUND_RATE = BigDecimal.valueOf(0.5);

    private final LocalDateTime checkInDate;
    private final LocalDateTime refundRequestDate;
}
```

**환불 정책**:

| 체크인까지 남은 일수   | 환불율        |
|---------------|------------|
| 7일 이상         | 100%       |
| 3일 이상 ~ 7일 미만 | 50%        |
| 3일 미만         | 0% (환불 불가) |

**주요 메서드**:

```java
// 팩토리 메서드
public static RefundPolicy of(
    LocalDateTime checkInDate,
    LocalDateTime refundRequestDate
)

// 환불 금액 계산
public Money calculateRefundAmount(Money originalAmount)

// 환불 가능 여부
public boolean isRefundable()

// 환불율 조회
public int getRefundRate()  // 100, 50, 또는 0 반환
```

**사용 예시**:

```java
RefundPolicy policy = RefundPolicy.of(checkInDate, LocalDateTime.now());
Money refundAmount = policy.calculateRefundAmount(payment.getAmount());

if (policy.isRefundable()) {
    // 환불 처리
}
```

---

## Domain Event

### PaymentEvent (Outbox)

**패키지**: `com.teambind.payment.domain.PaymentEvent`

**책임**:

- 도메인 이벤트 저장 (Outbox Pattern)
- 이벤트 발행 상태 관리
- 재시도 로직 지원

**속성**:

```java
@Entity
@Table(name = "payment_events")
public class PaymentEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    private String aggregateId;         // Payment ID 또는 Refund ID

    @Enumerated(EnumType.STRING)
    private EventType eventType;        // PAYMENT_COMPLETED, PAYMENT_CANCELLED, REFUND_COMPLETED

    private String payload;             // JSON 형식의 이벤트 데이터

    @Enumerated(EnumType.STRING)
    private EventStatus status;         // PENDING, PUBLISHED, FAILED

    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private Integer retryCount;         // 재시도 횟수
    private String errorMessage;
}
```

**이벤트 타입**:

```java
public enum EventType {
    PAYMENT_COMPLETED,      // 결제 완료
    PAYMENT_CANCELLED,      // 결제 취소
    REFUND_COMPLETED        // 환불 완료
}
```

**이벤트 상태**:

```java
public enum EventStatus {
    PENDING,        // 발행 대기
    PUBLISHED,      // 발행 완료
    FAILED          // 발행 실패
}
```

**주요 메서드**:

```java
// 이벤트 생성
public static PaymentEvent create(
    String aggregateId,
    EventType eventType,
    String payload
)

// 상태 전이
public void markAsPublished()
public void markAsFailed(String errorMessage)
public void resetForRetry()

// 상태 확인
public boolean isPending()
public boolean isPublished()
public boolean isFailed()
public boolean canRetry(int maxRetryCount)
```

**Outbox Pattern 플로우**:

```
1. 비즈니스 트랜잭션 내에서 PaymentEvent 생성 및 저장
2. OutboxEventScheduler가 주기적으로 PENDING 이벤트 조회
3. Kafka로 이벤트 발행
4. 성공 시 markAsPublished() 호출
5. 실패 시 markAsFailed() 호출 및 재시도
```

---

## Enum

### PaymentStatus

```java
public enum PaymentStatus {
    PREPARED,       // 결제 준비 완료 (승인 대기)
    COMPLETED,      // 결제 완료
    FAILED,         // 결제 실패
    CANCELLED       // 결제 취소
}
```

### PaymentMethod

```java
public enum PaymentMethod {
    CARD,               // 카드 결제
    VIRTUAL_ACCOUNT,    // 가상계좌
    EASY_PAY            // 간편결제 (토스페이 등)
}
```

### RefundStatus

```java
public enum RefundStatus {
    PENDING,        // 환불 요청 대기
    APPROVED,       // 환불 승인
    COMPLETED,      // 환불 완료
    FAILED          // 환불 실패
}
```

---

## 불변성 및 캡슐화

### 1. Entity 생성자 보호

모든 Entity는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용하여 직접 생성을 방지하고, 정적 팩토리 메서드를 통해서만 생성 가능:

```java
// ❌ 직접 생성 불가
Payment payment = new Payment();

// ✅ 팩토리 메서드 사용
Payment payment = Payment.prepare(reservationId, amount, checkInDate);
```

### 2. Value Object 불변성

Money는 완전한 불변 객체이며, 모든 연산은 새로운 객체를 반환:

```java
Money original = Money.of(10000);
Money doubled = original.multiply(BigDecimal.valueOf(2));
// original은 변경되지 않음
```

### 3. 상태 변경 캡슐화

Entity의 상태 변경은 의미 있는 메서드를 통해서만 가능:

```java
// ❌ 직접 상태 변경 불가
payment.setStatus(PaymentStatus.COMPLETED);

// ✅ 도메인 메서드 사용
payment.complete(orderId, paymentKey, transactionId, method);
```

---

## 도메인 규칙 검증

### 1. Entity 생성 시점 검증

```java
public static Payment prepare(String reservationId, Money amount, LocalDateTime checkInDate) {
    validateReservationId(reservationId);
    validateAmountNotNull(amount);
    validateCheckInDate(checkInDate);

    // 검증 통과 후 생성
    return Payment.builder()
            .paymentId(generatePaymentId())
            .reservationId(reservationId)
            .amount(amount)
            .status(PaymentStatus.PREPARED)
            .checkInDate(checkInDate)
            .idempotencyKey(generateIdempotencyKey(reservationId))
            .createdAt(LocalDateTime.now())
            .build();
}
```

### 2. 상태 전이 검증

```java
public void complete(String orderId, String paymentKey, String transactionId, PaymentMethod method) {
    validatePreparedStatus();  // PREPARED 상태가 아니면 예외
    validateOrderId(orderId);
    validatePaymentKey(paymentKey);

    // 검증 통과 후 상태 변경
    this.status = PaymentStatus.COMPLETED;
    this.orderId = orderId;
    this.paymentKey = paymentKey;
    this.transactionId = transactionId;
    this.method = method;
    this.paidAt = LocalDateTime.now();
}
```

### 3. 비즈니스 규칙 검증

```java
public void validateRefundable() {
    if (this.status != PaymentStatus.COMPLETED) {
        throw new IllegalStateException("완료된 결제만 환불 가능합니다");
    }

    if (LocalDateTime.now().isAfter(this.checkInDate)) {
        throw new IllegalStateException("체크인 날짜 이후에는 환불이 불가능합니다");
    }
}
```

---

## 도메인 모델 테스트

### Entity 테스트 예시

```java
@Test
@DisplayName("결제 준비 성공")
void prepare_success() {
    // given
    String reservationId = "reservation-123";
    Money amount = Money.of(100000L);
    LocalDateTime checkInDate = LocalDateTime.now().plusDays(7);

    // when
    Payment payment = Payment.prepare(reservationId, amount, checkInDate);

    // then
    assertThat(payment.getPaymentId()).startsWith("PAY-");
    assertThat(payment.getReservationId()).isEqualTo(reservationId);
    assertThat(payment.getAmount()).isEqualTo(amount);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PREPARED);
    assertThat(payment.getIdempotencyKey()).startsWith("IDEM-");
}
```

### Value Object 테스트 예시

```java
@Test
@DisplayName("금액 곱셈 연산")
void multiply() {
    // given
    Money original = Money.of(10000);

    // when
    Money half = original.multiply(BigDecimal.valueOf(0.5));

    // then
    assertThat(half.getValue()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    assertThat(original.getValue()).isEqualByComparingTo(BigDecimal.valueOf(10000));
}
```

### RefundPolicy 테스트 예시

```java
@Test
@DisplayName("7일 이상 남은 경우 100% 환불")
void calculateRefundAmount_fullRefund() {
    // given
    LocalDateTime checkInDate = LocalDateTime.now().plusDays(10);
    RefundPolicy policy = RefundPolicy.of(checkInDate, LocalDateTime.now());
    Money originalAmount = Money.of(100000L);

    // when
    Money refundAmount = policy.calculateRefundAmount(originalAmount);

    // then
    assertThat(refundAmount).isEqualTo(originalAmount);
    assertThat(policy.getRefundRate()).isEqualTo(100);
}
```

---

## 관련 문서

- [Payment Flow](../features/PAYMENT_FLOW.md)
- [Refund Flow](../features/REFUND_FLOW.md)
- [Exception Handling](EXCEPTION_HANDLING.md)
- [ADR-005: Refund Policy Design](../adr/005-refund-policy-design.md)

---

**최종 업데이트**: 2025-11-23
**작성자**: TeamBind
