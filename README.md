# Toss Payment Service

> v0.0.1-SNAPSHOT | TeamBind | Java 21 | Spring Boot 3.5.7 | MariaDB | Kafka 3.6

**Java 21 Features**

- Pattern Matching for switch
- Record Classes
- Sealed Classes
- Virtual Threads (Kafka Consumer)

## 목차

- [프로젝트 개요](#프로젝트-개요)
- [핵심 기능](#핵심-기능)
- [아키텍처](#아키텍처)
- [도메인 모델](#도메인-모델)
- [디자인 패턴](#디자인-패턴)
- [환불 정책](#환불-정책)
- [이벤트 기반 통신](#이벤트-기반-통신)
- [데이터베이스 스키마](#데이터베이스-스키마)
- [API 엔드포인트](#api-엔드포인트)
- [기술 스택](#기술-스택)
- [테스트](#테스트)
- [실행 방법](#실행-방법)
- [프로젝트 분석](#프로젝트-분석)
- [향후 개선사항](#향후-개선사항)

---

## 프로젝트 개요

**비즈니스 목적**

공간 예약 플랫폼의 결제/환불 처리를 담당하는 독립적인 MSA 서비스입니다. Toss Payments API와 통합하여 안전하고 신뢰성 있는 결제 프로세스를 제공하며, MSA 환경에서 발생할 수 있는 분산 트랜잭션
문제를 Outbox Pattern으로 해결합니다.

**핵심 가치**

1. **이벤트 기반 결제 준비**: 예약 생성 시 Kafka 이벤트로 결제 정보 사전 준비
2. **금액 검증**: 사전 저장된 금액과 비교하여 클라이언트 변조 차단
3. **신뢰성 있는 이벤트**: Outbox Pattern으로 결제 결과 이벤트 유실 방지
4. **유연한 환불**: 예약 서비스 연동으로 체크인 날짜 기반 차등 환불 정책
5. **멱등성 보장**: reservationId 기반 중복 결제 자동 방지

**MSA 생태계 내 역할**

```
[예약 서비스] ─────> Kafka Event ─────> [결제 서비스]
                (reservationId, amount)   Payment PREPARED 상태 저장
                                                   ↓
[클라이언트] ───> Toss 결제창 ───> successUrl ───> 결제 승인 API
                                                   ↓
                                           Toss Payments API
                                                   ↓
                                         Payment COMPLETED 저장
                                                   ↓
                                    Kafka (결제 완료/실패 이벤트)
                                                   ↓
                               [예약 서비스, 알림 서비스, 정산 서비스]

[환불 요청] ───> [결제 서비스] ───> 예약 서비스 동기 호출 ───> 체크인 날짜 조회
                                            ↓
                                    환불율 계산 + Toss 환불 API
                                            ↓
                                     Kafka (환불 완료 이벤트)
```

---

## 핵심 기능

### 1. 이벤트 기반 결제 준비 (Event-Driven Payment Preparation)

예약 생성 시 Kafka 이벤트로 결제 정보를 사전 준비하여 금액 검증과 멱등성 보장

**이벤트 수신 및 Payment 준비**

```java
// ReservationEventConsumer.java
@Component
@RequiredArgsConstructor
public class ReservationEventConsumer {

    private final PaymentRepository paymentRepository;

    @KafkaListener(topics = "reservation-events", groupId = "payment-service")
    @Transactional
    public void handleReservationEvent(String message) {
        ReservationPaymentEvent event = parseEvent(message);

        if (event.eventType().equals("RESERVATION_PAYMENT_REQUIRED")) {
            // 멱등성: 이미 존재하면 스킵
            if (paymentRepository.existsByReservationId(event.reservationId())) {
                return;
            }

            // Payment PREPARED 상태로 저장
            Payment payment = Payment.prepare(
                event.reservationId(),
                event.amount()
            );
            paymentRepository.save(payment);
        }
    }
}

// Kafka 이벤트 구조
{
  "eventId": "EVT-20251121-001",
  "eventType": "RESERVATION_PAYMENT_REQUIRED",
  "reservationId": "RSV-20251121-001",
  "amount": 50000,
  "timestamp": "2025-11-21T10:00:00"
}
```

**결제 승인 (Toss 결제창 완료 후)**

```java
// PaymentConfirmService.java
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentConfirmService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentEventPublisher eventPublisher;

    public Payment confirm(PaymentConfirmRequest request) {
        // 1. 사전 준비된 Payment 조회
        Payment payment = paymentRepository
            .findByReservationId(request.orderId())
            .orElseThrow(() -> new PaymentNotFoundException(
                "준비되지 않은 결제: " + request.orderId()
            ));

        // 2. 금액 검증 (클라이언트 변조 방지)
        if (!payment.getAmount().getValue().equals(request.amount())) {
            throw new PaymentAmountMismatchException(
                "금액 불일치 - 저장: " + payment.getAmount() +
                ", 요청: " + request.amount()
            );
        }

        // 3. Toss 승인 API 호출
        TossPaymentResponse tossResponse = tossPaymentClient.confirm(
            request.paymentKey(),
            request.orderId(),
            request.amount()
        );

        // 4. Payment 완료 처리
        payment.complete(tossResponse);
        paymentRepository.save(payment);

        // 5. 완료 이벤트 발행 (Outbox)
        eventPublisher.publishCompleted(payment);

        return payment;
    }
}

// 클라이언트 요청 (토스 successUrl에서)
POST /api/v1/payments/confirm
{
  "paymentKey": "토스가_준_값",
  "orderId": "RSV-20251121-001",
  "amount": 50000
}
```

---

### 2. 환불 처리 (Refund Processing)

예약 서비스 연동으로 체크인 날짜 조회 후 시간 기반 차등 환불율 적용

**환불 요청 처리**

```java
// RefundService.java
@Service
@RequiredArgsConstructor
@Transactional
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final ReservationClient reservationClient;
    private final RefundPolicyService refundPolicyService;
    private final TossPaymentClient tossPaymentClient;
    private final RefundRepository refundRepository;
    private final RefundEventPublisher eventPublisher;

    public Refund processRefund(RefundRequest request) {
        // 1. Payment 조회
        Payment payment = paymentRepository
            .findById(request.paymentId())
            .orElseThrow(() -> new PaymentNotFoundException());

        // 2. 예약 서비스에서 체크인 날짜 조회
        ReservationInfo reservation = reservationClient
            .getReservation(payment.getReservationId());

        // 3. 환불 정책 계산
        RefundCalculation calculation = refundPolicyService.calculate(
            payment.getAmount(),
            payment.getPaidAt(),
            reservation.getCheckInDate(),
            LocalDateTime.now()
        );

        // 4. Toss 환불 API 호출
        TossRefundResponse tossResponse = tossPaymentClient.refund(
            payment.getPaymentKey(),
            calculation.getRefundAmount().getValue()
        );

        // 5. Refund 엔티티 저장
        Refund refund = Refund.of(payment, calculation);
        refund.complete(tossResponse);
        refundRepository.save(refund);

        // 6. 환불 완료 이벤트 발행 (Outbox)
        eventPublisher.publishRefundCompleted(refund);

        return refund;
    }
}

// 클라이언트 요청
POST /api/v1/refunds
{
  "paymentId": "PAY-20251121-001",
  "reason": "고객 요청"
}
```

**환불 정책 서비스 (Domain Service)**

```java
// RefundPolicyService.java
@Component
public class RefundPolicyService {

    public RefundCalculation calculate(
        Money amount,
        LocalDateTime paidAt,
        LocalDateTime checkInDate,
        LocalDateTime refundRequestAt
    ) {
        // 1. 10분 이내 무료 취소 체크
        Duration timeSincePayment = Duration.between(paidAt, refundRequestAt);
        if (timeSincePayment.toMinutes() <= 10) {
            return RefundCalculation.fullRefund(amount, true);
        }

        // 2. 체크인까지 남은 일수 계산
        long daysUntilCheckIn = ChronoUnit.DAYS.between(
            refundRequestAt.toLocalDate(),
            checkInDate.toLocalDate()
        );

        // 3. 차등 환불율 적용
        BigDecimal refundRate = determineRefundRate(daysUntilCheckIn);

        Money refundAmount = amount.multiply(refundRate);

        return new RefundCalculation(refundAmount, refundRate, false);
    }

    // Java 21 Pattern Matching for switch
    private BigDecimal determineRefundRate(long daysUntilCheckIn) {
        return switch ((int) daysUntilCheckIn) {
            case int days when days >= 5 -> BigDecimal.valueOf(1.00);  // 100%
            case 4 -> BigDecimal.valueOf(0.70);  // 70%
            case 3 -> BigDecimal.valueOf(0.50);  // 50%
            case 2 -> BigDecimal.valueOf(0.30);  // 30%
            case 1 -> BigDecimal.valueOf(0.10);  // 10%
            default -> BigDecimal.ZERO;  // 당일 환불 불가
        };
    }
}
```

**환불 시나리오 예시**

```
결제 금액: 100,000원
체크인 날짜: 2025-11-25

환불 요청 시점        환불 금액      수수료
2025-11-20 (5일 전)   100,000원     무료 (결제 후 10분 이내)
2025-11-20 (5일 전)   100,000원     유료 (결제 후 10분 경과)
2025-11-21 (4일 전)    70,000원     유료
2025-11-22 (3일 전)    50,000원     유료
2025-11-23 (2일 전)    30,000원     유료
2025-11-24 (1일 전)    10,000원     유료
2025-11-25 (당일)      환불 불가     -
```

---

### 3. Outbox Pattern 기반 이벤트 발행

DB 트랜잭션과 Kafka 이벤트 발행의 원자성 보장

```java
// PaymentEvent Entity (Outbox Table)
@Entity
@Table(name = "payment_events")
public class PaymentEvent {
    @Id
    private String eventId;

    @Column(nullable = false)
    private String aggregateId;  // paymentId or reservationId

    @Enumerated(EnumType.STRING)
    private EventType eventType;  // PAYMENT_COMPLETED, PAYMENT_FAILED

    @Column(columnDefinition = "TEXT")
    private String payload;  // JSON

    @Enumerated(EnumType.STRING)
    private EventStatus status;  // PENDING, PUBLISHED, FAILED

    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;

    // Factory method
    public static PaymentEvent completed(Payment payment) {
        return PaymentEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .aggregateId(payment.getPaymentId())
            .eventType(EventType.PAYMENT_COMPLETED)
            .payload(toJson(payment))
            .status(EventStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build();
    }
}

// 스케줄러가 주기적으로 Pending 이벤트 발행
@Component
public class PaymentEventPublisher {

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<PaymentEvent> pending = eventRepository
            .findByStatusOrderByCreatedAtAsc(EventStatus.PENDING);

        for (PaymentEvent event : pending) {
            try {
                kafkaTemplate.send("payment-events", event.getPayload());
                event.markAsPublished();
            } catch (Exception e) {
                event.incrementRetryCount();
                if (event.getRetryCount() > MAX_RETRY) {
                    event.markAsFailed();
                }
            }
            eventRepository.save(event);
        }
    }
}
```

**Kafka 페이로드 구조**

```json
{
  "eventType": "PAYMENT_COMPLETED",
  "reservationId": "RSV-20251120-001",
  "paymentId": "PAY-20251120-001",
  "success": true,
  "amount": 50000,
  "paidAt": "2025-11-20T14:30:00",
  "timestamp": "2025-11-20T14:30:01"
}

// 실패 시
{
  "eventType": "PAYMENT_FAILED",
  "reservationId": "RSV-20251120-001",
  "paymentId": null,
  "success": false,
  "failureReason": "금액 불일치: 요청 50000원, 실제 48000원",
  "timestamp": "2025-11-20T14:30:01"
}
```

---

## 아키텍처

### Hexagonal Architecture (Ports & Adapters)

도메인 로직을 중심에 두고 외부 기술(Framework, DB, API)을 교체 가능하게 설계

```
┌─────────────────────────────────────────────────────────────┐
│                         Adapter In                           │
│                    (Web Controller)                          │
│                   PaymentController                          │
│                   RefundController                           │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                         │
│                                                               │
│  ┌─────────────────────────────────────────────────┐        │
│  │         Port In (Use Cases)                     │        │
│  │  - ProcessPaymentUseCase                        │        │
│  │  - ProcessRefundUseCase                         │        │
│  │  - QueryPaymentUseCase                          │        │
│  └─────────────────────────────────────────────────┘        │
│                        │                                     │
│  ┌─────────────────────▼─────────────────────────┐          │
│  │      Application Service                      │          │
│  │  - PaymentService                             │          │
│  │  - RefundService                              │          │
│  └─────────────────────┬─────────────────────────┘          │
│                        │                                     │
│  ┌─────────────────────▼─────────────────────────┐          │
│  │         Port Out                              │          │
│  │  - LoadReservationPort                        │          │
│  │  - ProcessTossPaymentPort                     │          │
│  │  - SavePaymentPort                            │          │
│  │  - PublishEventPort                           │          │
│  └───────────────────────────────────────────────┘          │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer                            │
│                                                               │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │ Payment          │  │ Refund           │  Aggregates     │
│  │ (Aggregate Root) │  │ (Aggregate Root) │                 │
│  └──────────────────┘  └──────────────────┘                 │
│                                                               │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │ Money            │  │ PaymentMethod    │  Value Objects  │
│  │ RefundPolicy     │  │ PaymentStatus    │                 │
│  └──────────────────┘  └──────────────────┘                 │
│                                                               │
│  ┌──────────────────┐                                        │
│  │ RefundPolicyService │  Domain Service                    │
│  └──────────────────┘                                        │
│                                                               │
│  ┌──────────────────┐                                        │
│  │ PaymentEvent     │  Domain Event (Outbox)                │
│  └──────────────────┘                                        │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                      Adapter Out                             │
│                                                               │
│  ┌────────────────────────────────────────────┐             │
│  │  Persistence (JPA)                         │             │
│  │  - PaymentJpaAdapter                       │             │
│  │  - PaymentEventJpaAdapter                  │             │
│  └────────────────────────────────────────────┘             │
│                                                               │
│  ┌────────────────────────────────────────────┐             │
│  │  External API (Feign)                      │             │
│  │  - ReservationClient                       │             │
│  │  - TossPaymentClient                       │             │
│  └────────────────────────────────────────────┘             │
│                                                               │
│  ┌────────────────────────────────────────────┐             │
│  │  Event (Kafka)                             │             │
│  │  - PaymentEventPublisher                   │             │
│  └────────────────────────────────────────────┘             │
└─────────────────────────────────────────────────────────────┘
```

**의존성 역전 원칙 (DIP)**

도메인 레이어는 외부 레이어에 의존하지 않습니다. 모든 의존성은 Port 인터페이스를 통해 역전됩니다.

```java
// Domain Layer (Port 정의)
public interface LoadReservationPort {
    ReservationInfo getReservation(String reservationId);
}

// Adapter Layer (Port 구현)
@Component
public class ReservationClientAdapter implements LoadReservationPort {

    private final ReservationFeignClient feignClient;

    @Override
    public ReservationInfo getReservation(String reservationId) {
        ReservationResponse response = feignClient.getReservation(reservationId);
        return ReservationInfo.from(response);
    }
}
```

---

## 도메인 모델

### Payment Aggregate

```java
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    private String paymentId;

    @Column(nullable = false)
    private String reservationId;

    @Embedded
    private Money amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    // Toss Payments 연동 필드
    private String orderId;
    private String paymentKey;
    private String transactionId;

    // 환불 계산용
    private LocalDateTime checkInDate;

    // 멱등성 보장
    @Column(unique = true)
    private String idempotencyKey;

    // 감사 추적
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private String failureReason;

    // Factory Method
    public static Payment create(PaymentCommand command, ReservationInfo reservation) {
        validateAmount(command.getAmount(), reservation.getAmount());

        return Payment.builder()
            .paymentId(generatePaymentId())
            .reservationId(command.getReservationId())
            .amount(Money.of(command.getAmount()))
            .method(command.getPaymentMethod())
            .status(PaymentStatus.PENDING)
            .checkInDate(reservation.getCheckInDate())
            .idempotencyKey(command.getIdempotencyKey())
            .createdAt(LocalDateTime.now())
            .build();
    }

    // 비즈니스 로직: 결제 완료
    public void complete(TossPaymentResponse tossResponse) {
        if (this.status != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(
                "PENDING 상태에서만 완료 처리 가능합니다"
            );
        }

        this.status = PaymentStatus.COMPLETED;
        this.orderId = tossResponse.getOrderId();
        this.paymentKey = tossResponse.getPaymentKey();
        this.transactionId = tossResponse.getTransactionId();
        this.paidAt = LocalDateTime.now();
    }

    // 비즈니스 로직: 결제 실패
    public void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    // 비즈니스 로직: 환불 가능 여부 검증
    public void validateRefundable() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new InvalidPaymentStateException(
                "완료된 결제만 환불 가능합니다"
            );
        }

        if (LocalDateTime.now().isAfter(this.checkInDate)) {
            throw new RefundNotAllowedException(
                "체크인 날짜 이후에는 환불이 불가능합니다"
            );
        }
    }

    private static void validateAmount(BigDecimal requested, BigDecimal actual) {
        if (!requested.equals(actual)) {
            throw new PaymentAmountMismatchException(
                "요청 금액: " + requested + ", 실제 금액: " + actual
            );
        }
    }
}
```

### Refund Aggregate

```java
@Entity
@Table(name = "refunds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    private String refundId;

    @Column(nullable = false)
    private String paymentId;

    @Embedded
    private Money refundAmount;

    @Embedded
    private Money originalAmount;

    private BigDecimal refundRate;
    private boolean commissionFree;

    @Enumerated(EnumType.STRING)
    private RefundStatus status;

    private String tossRefundKey;

    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private String failureReason;

    // Factory Method
    public static Refund of(Payment payment, RefundCalculation calculation) {
        payment.validateRefundable();

        return Refund.builder()
            .refundId(generateRefundId())
            .paymentId(payment.getPaymentId())
            .refundAmount(calculation.getRefundAmount())
            .originalAmount(payment.getAmount())
            .refundRate(calculation.getRefundRate())
            .commissionFree(calculation.isCommissionFree())
            .status(RefundStatus.PENDING)
            .requestedAt(LocalDateTime.now())
            .build();
    }

    // 비즈니스 로직: 환불 완료
    public void complete(TossRefundResponse tossResponse) {
        if (this.status != RefundStatus.PENDING) {
            throw new InvalidRefundStateException(
                "PENDING 상태에서만 완료 처리 가능합니다"
            );
        }

        this.status = RefundStatus.COMPLETED;
        this.tossRefundKey = tossResponse.getRefundKey();
        this.completedAt = LocalDateTime.now();
    }
}
```

### Value Objects

**Money - 금액 표현**

```java
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {

    @Column(name = "amount", nullable = false)
    private BigDecimal value;

    @Column(name = "currency", nullable = false)
    private String currency;

    public static final Money ZERO = new Money(BigDecimal.ZERO, "KRW");

    private Money(BigDecimal value, String currency) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다");
        }
        this.value = value.setScale(0, RoundingMode.DOWN);
        this.currency = currency;
    }

    public static Money of(BigDecimal value) {
        return new Money(value, "KRW");
    }

    public Money multiply(BigDecimal multiplier) {
        return new Money(this.value.multiply(multiplier), this.currency);
    }

    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(this.value.add(other.value), this.currency);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return value.compareTo(money.value) == 0 &&
               currency.equals(money.currency);
    }
}
```

---

## 디자인 패턴

### 1. Factory Pattern (객체 생성 캡슐화)

복잡한 생성 로직과 검증을 Factory Method에 캡슐화하여 불변성 보장

```java
// Payment Entity
public static Payment create(PaymentCommand command, ReservationInfo reservation) {
    // 생성 시점 검증
    validateAmount(command.getAmount(), reservation.getAmount());
    validateIdempotencyKey(command.getIdempotencyKey());

    return Payment.builder()
        .paymentId(generatePaymentId())  // ID 생성 로직 캡슐화
        .reservationId(command.getReservationId())
        .amount(Money.of(command.getAmount()))
        .status(PaymentStatus.PENDING)
        .checkInDate(reservation.getCheckInDate())
        .idempotencyKey(command.getIdempotencyKey())
        .createdAt(LocalDateTime.now())
        .build();
}

// 잘못된 상태의 객체 생성 불가
Payment payment = new Payment();  // 컴파일 에러 (protected 생성자)
```

---

### 2. Strategy Pattern (환불 정책 추상화)

향후 프리미엄 회원, 시즌별 정책 등 다양한 환불 전략 확장 가능

```java
// 현재 구현
public interface RefundStrategy {
    Money calculateRefund(Money originalAmount, long daysUntilCheckIn);
    boolean isCommissionFree(Duration timeSincePayment);
}

@Component
public class StandardRefundStrategy implements RefundStrategy {

    private static final Map<Long, BigDecimal> REFUND_RATES = Map.of(
        5L, BigDecimal.valueOf(1.00),
        4L, BigDecimal.valueOf(0.70),
        3L, BigDecimal.valueOf(0.50),
        2L, BigDecimal.valueOf(0.30),
        1L, BigDecimal.valueOf(0.10)
    );

    @Override
    public Money calculateRefund(Money originalAmount, long daysUntilCheckIn) {
        BigDecimal rate = REFUND_RATES.entrySet().stream()
            .filter(e -> daysUntilCheckIn >= e.getKey())
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(BigDecimal.ZERO);

        return originalAmount.multiply(rate);
    }

    @Override
    public boolean isCommissionFree(Duration timeSincePayment) {
        return timeSincePayment.toMinutes() <= 10;
    }
}

// 향후 확장 예시
@Component
public class PremiumRefundStrategy implements RefundStrategy {
    @Override
    public Money calculateRefund(Money originalAmount, long daysUntilCheckIn) {
        // 프리미엄 회원: 전액 환불 기간 연장
        return daysUntilCheckIn >= 3
            ? originalAmount
            : originalAmount.multiply(BigDecimal.valueOf(0.80));
    }
}
```

---

### 3. Outbox Pattern (이벤트 신뢰성)

DB 트랜잭션과 메시지 발행의 원자성 보장으로 At-least-once 전달 보장

```java
// 1단계: 비즈니스 로직 + 이벤트 저장 (같은 트랜잭션)
@Transactional
public PaymentResult processPayment(PaymentCommand command) {
    Payment payment = Payment.create(command, reservation);
    payment.complete(tossResponse);

    paymentRepository.save(payment);
    eventRepository.save(PaymentEvent.completed(payment));  // Outbox 저장

    return PaymentResult.success(payment);
}

// 2단계: 스케줄러가 비동기로 발행 (별도 트랜잭션)
@Scheduled(fixedDelay = 1000)
@Transactional
public void publishPendingEvents() {
    List<PaymentEvent> pending = eventRepository.findPending();

    for (PaymentEvent event : pending) {
        try {
            kafkaTemplate.send("payment-events", event.getPayload());
            event.markAsPublished();
            eventRepository.save(event);
        } catch (Exception e) {
            // 재시도 로직
            event.incrementRetryCount();
            eventRepository.save(event);
        }
    }
}
```

**장점:**

- 이벤트 유실 방지
- DB와 Kafka 사이 원자성 보장
- 자동 재시도
- 이벤트 발행 이력 추적

---

## 환불 정책

### 시간 기반 차등 환불율

| 환불 요청 시점   | 환불율   | 비고      |
|------------|-------|---------|
| 이용 5일 전 이상 | 100%  | 전액 환불   |
| 이용 4일 전    | 70%   | 30% 수수료 |
| 이용 3일 전    | 50%   | 50% 수수료 |
| 이용 2일 전    | 30%   | 70% 수수료 |
| 이용 1일 전    | 10%   | 90% 수수료 |
| 이용 당일      | 환불 불가 | -       |

### 결제 후 10분 무료 취소

결제 완료 후 10분 이내 취소 시 수수료 면제 (100% 환불)

```java
// RefundPolicyService
public RefundCalculation calculate(
    Money amount,
    LocalDateTime paidAt,
    LocalDateTime checkInDate,
    LocalDateTime refundRequestAt
) {
    // 10분 이내 체크
    Duration timeSincePayment = Duration.between(paidAt, refundRequestAt);
    if (timeSincePayment.toMinutes() <= 10) {
        return RefundCalculation.fullRefund(amount, true);
    }

    // 일반 환불 정책 적용
    long daysUntilCheckIn = ChronoUnit.DAYS.between(refundRequestAt, checkInDate);
    BigDecimal refundRate = determineRefundRate(daysUntilCheckIn);

    return new RefundCalculation(
        amount.multiply(refundRate),
        refundRate,
        false
    );
}
```

---

## 이벤트 기반 통신

### Kafka Topic 구조

**단일 토픽 전략**: `payment-events`

```json
{
  "eventId": "EVT-20251120-001",
  "eventType": "PAYMENT_COMPLETED",
  "reservationId": "RSV-20251120-001",
  "paymentId": "PAY-20251120-001",
  "success": true,
  "amount": 50000,
  "paidAt": "2025-11-20T14:30:00",
  "timestamp": "2025-11-20T14:30:01.234"
}
```

**실패 이벤트**

```json
{
  "eventId": "EVT-20251120-002",
  "eventType": "PAYMENT_FAILED",
  "reservationId": "RSV-20251120-001",
  "paymentId": null,
  "success": false,
  "failureReason": "금액 불일치",
  "timestamp": "2025-11-20T14:30:01.234"
}
```

### 컨슈머 처리 예상 (예약 서비스)

```java
@KafkaListener(topics = "payment-events")
public void handlePaymentEvent(PaymentEventPayload payload) {
    if (payload.isSuccess()) {
        reservationService.confirmReservation(payload.getReservationId());
    } else {
        reservationService.failReservation(
            payload.getReservationId(),
            payload.getFailureReason()
        );
    }
}
```

---

## 데이터베이스 스키마

### ERD

```
┌─────────────────────────────────────────────┐
│              payments                        │
├─────────────────────────────────────────────┤
│ payment_id (PK)          VARCHAR(50)        │
│ reservation_id           VARCHAR(50)        │ FK → Reservation Service
│ amount                   DECIMAL(15,2)      │
│ currency                 VARCHAR(3)         │
│ method                   VARCHAR(20)        │ ENUM
│ status                   VARCHAR(20)        │ ENUM
│ order_id                 VARCHAR(100)       │ Toss
│ payment_key              VARCHAR(200)       │ Toss
│ transaction_id           VARCHAR(100)       │ Toss
│ check_in_date            DATETIME           │
│ idempotency_key          VARCHAR(100) UK    │
│ created_at               DATETIME           │
│ paid_at                  DATETIME           │
│ cancelled_at             DATETIME           │
│ failure_reason           TEXT               │
└─────────────────────────────────────────────┘
                    │
                    │ 1:N
                    ▼
┌─────────────────────────────────────────────┐
│              refunds                         │
├─────────────────────────────────────────────┤
│ refund_id (PK)           VARCHAR(50)        │
│ payment_id (FK)          VARCHAR(50)        │
│ refund_amount            DECIMAL(15,2)      │
│ original_amount          DECIMAL(15,2)      │
│ refund_rate              DECIMAL(3,2)       │
│ commission_free          BOOLEAN            │
│ status                   VARCHAR(20)        │ ENUM
│ toss_refund_key          VARCHAR(200)       │
│ requested_at             DATETIME           │
│ completed_at             DATETIME           │
│ failure_reason           TEXT               │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│          payment_events (Outbox)             │
├─────────────────────────────────────────────┤
│ event_id (PK)            VARCHAR(50)        │
│ aggregate_id             VARCHAR(50)        │
│ event_type               VARCHAR(50)        │
│ payload                  TEXT               │ JSON
│ status                   VARCHAR(20)        │ ENUM
│ retry_count              INT                │
│ created_at               DATETIME           │
│ published_at             DATETIME           │
│                                              │
│ INDEX idx_status (status, created_at)       │
└─────────────────────────────────────────────┘
```

### 인덱스 전략

```sql
-- 멱등성 검증 (중복 결제 방지)
CREATE UNIQUE INDEX uk_idempotency_key ON payments(idempotency_key);

-- 예약 ID로 결제 조회
CREATE INDEX idx_reservation_id ON payments(reservation_id);

-- 결제 상태별 조회
CREATE INDEX idx_status_created_at ON payments(status, created_at);

-- Outbox 이벤트 발행 쿼리 최적화
CREATE INDEX idx_event_status_created ON payment_events(status, created_at);

-- 환불 내역 조회
CREATE INDEX idx_payment_id ON refunds(payment_id);
```

---

## API 엔드포인트

### 1. 결제 요청

```http
POST /api/v1/payments
Content-Type: application/json

{
  "reservationId": "RSV-20251120-001",
  "amount": 50000,
  "paymentMethod": "CARD",
  "idempotencyKey": "unique-key-12345"
}

# 성공 응답 (201 Created)
{
  "paymentId": "PAY-20251120-001",
  "status": "COMPLETED",
  "amount": 50000,
  "paidAt": "2025-11-20T14:30:00"
}

# 실패 응답 (409 Conflict)
{
  "errorCode": "PAYMENT_AMOUNT_MISMATCH",
  "message": "금액 불일치: 요청 50000원, 실제 48000원",
  "timestamp": "2025-11-20T14:30:00"
}
```

---

### 2. 결제 조회

```http
GET /api/v1/payments/{paymentId}

# 응답 (200 OK)
{
  "paymentId": "PAY-20251120-001",
  "reservationId": "RSV-20251120-001",
  "amount": 50000,
  "method": "CARD",
  "status": "COMPLETED",
  "paidAt": "2025-11-20T14:30:00"
}
```

---

### 3. 환불 요청

```http
POST /api/v1/refunds
Content-Type: application/json

{
  "paymentId": "PAY-20251120-001",
  "reason": "고객 요청"
}

# 응답 (201 Created)
{
  "refundId": "REF-20251120-001",
  "paymentId": "PAY-20251120-001",
  "refundAmount": 50000,
  "refundRate": 1.00,
  "commissionFree": true,
  "status": "COMPLETED"
}
```

---

### 4. 환불 내역 조회

```http
GET /api/v1/refunds?paymentId={paymentId}

# 응답 (200 OK)
{
  "refunds": [
    {
      "refundId": "REF-20251120-001",
      "refundAmount": 50000,
      "refundRate": 1.00,
      "requestedAt": "2025-11-20T14:35:00",
      "completedAt": "2025-11-20T14:35:30"
    }
  ]
}
```

---

## 기술 스택

### Backend

| 분류            | 기술                     | 버전     | 용도                                                                 |
|---------------|------------------------|--------|--------------------------------------------------------------------|
| Language      | Java                   | 21 LTS | 메인 언어 (Pattern Matching, Records, Sealed Classes, Virtual Threads) |
| Framework     | Spring Boot            | 3.5.7  | 애플리케이션 프레임워크                                                       |
| ORM           | Spring Data JPA        | 3.5.7  | 데이터 접근                                                             |
| Database      | MariaDB                | 11.x   | 메인 데이터베이스                                                          |
| Message Queue | Kafka                  | 3.6    | 이벤트 발행 (Virtual Threads로 Consumer 최적화)                             |
| HTTP Client   | Spring Cloud OpenFeign | 4.x    | 예약 서비스 통신                                                          |
| Validation    | Hibernate Validator    | 8.x    | 입력 검증                                                              |

### Infrastructure

| 분류               | 기술             | 용도       |
|------------------|----------------|----------|
| Build Tool       | Gradle         | 의존성 관리   |
| Containerization | Docker         | 컨테이너화    |
| Orchestration    | Docker Compose | 로컬 개발 환경 |

### Testing

| 분류               | 기술             | 용도         |
|------------------|----------------|------------|
| Unit Test        | JUnit 5        | 단위 테스트     |
| Mocking          | Mockito        | Mock 객체 생성 |
| Integration Test | Testcontainers | 통합 테스트     |
| Assertion        | AssertJ        | 가독성 높은 단언문 |

---

## 테스트

### 테스트 전략

```
Domain Layer (Unit Test)
  ↓ 순수 Java, 빠른 실행
Service Layer (Integration Test)
  ↓ Spring Context, Mock 외부 의존성
Controller Layer (E2E Test)
  ↓ Testcontainers, 실제 DB/Kafka
```

### 도메인 테스트 (Given-When-Then)

```java
@DisplayName("결제 도메인 테스트")
class PaymentTest {

    @Test
    @DisplayName("금액이 일치하면 결제 생성 성공")
    void createPayment_Success() {
        // Given
        PaymentCommand command = PaymentCommand.builder()
            .reservationId("RSV-001")
            .amount(BigDecimal.valueOf(50000))
            .paymentMethod(PaymentMethod.CARD)
            .idempotencyKey("key-001")
            .build();

        ReservationInfo reservation = ReservationInfo.builder()
            .reservationId("RSV-001")
            .amount(BigDecimal.valueOf(50000))
            .checkInDate(LocalDateTime.now().plusDays(5))
            .build();

        // When
        Payment payment = Payment.create(command, reservation);

        // Then
        assertThat(payment.getReservationId()).isEqualTo("RSV-001");
        assertThat(payment.getAmount().getValue()).isEqualByComparingTo("50000");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("금액 불일치 시 예외 발생")
    void createPayment_AmountMismatch() {
        // Given
        PaymentCommand command = PaymentCommand.builder()
            .amount(BigDecimal.valueOf(50000))
            .build();

        ReservationInfo reservation = ReservationInfo.builder()
            .amount(BigDecimal.valueOf(48000))  // 불일치
            .build();

        // When & Then
        assertThatThrownBy(() -> Payment.create(command, reservation))
            .isInstanceOf(PaymentAmountMismatchException.class)
            .hasMessageContaining("요청 금액: 50000, 실제 금액: 48000");
    }
}
```

### 환불 정책 테스트

```java
@DisplayName("환불 정책 테스트")
class RefundPolicyServiceTest {

    private RefundPolicyService refundPolicyService;

    @BeforeEach
    void setUp() {
        refundPolicyService = new RefundPolicyService();
    }

    @Test
    @DisplayName("5일 전 취소 시 100% 환불")
    void calculate_5DaysBefore_FullRefund() {
        // Given
        Money amount = Money.of(BigDecimal.valueOf(100000));
        LocalDateTime paidAt = LocalDateTime.of(2025, 11, 15, 14, 0);
        LocalDateTime checkInDate = LocalDateTime.of(2025, 11, 25, 15, 0);
        LocalDateTime refundRequestAt = LocalDateTime.of(2025, 11, 20, 10, 0);

        // When
        RefundCalculation result = refundPolicyService.calculate(
            amount, paidAt, checkInDate, refundRequestAt
        );

        // Then
        assertThat(result.getRefundAmount().getValue())
            .isEqualByComparingTo("100000");
        assertThat(result.getRefundRate())
            .isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("결제 후 10분 이내 취소 시 수수료 면제")
    void calculate_Within10Minutes_CommissionFree() {
        // Given
        Money amount = Money.of(BigDecimal.valueOf(100000));
        LocalDateTime paidAt = LocalDateTime.of(2025, 11, 20, 14, 0);
        LocalDateTime refundRequestAt = LocalDateTime.of(2025, 11, 20, 14, 8);
        LocalDateTime checkInDate = LocalDateTime.of(2025, 11, 25, 15, 0);

        // When
        RefundCalculation result = refundPolicyService.calculate(
            amount, paidAt, checkInDate, refundRequestAt
        );

        // Then
        assertThat(result.isCommissionFree()).isTrue();
        assertThat(result.getRefundAmount().getValue())
            .isEqualByComparingTo("100000");
    }
}
```

---

## 실행 방법

### 로컬 개발 환경

```bash
# 1. 환경 변수 설정
cp .env.example .env
# .env 파일 수정 (Toss API Key, DB 정보 등)

# 2. Docker Compose로 인프라 실행
docker-compose up -d

# 3. 애플리케이션 실행
./gradlew bootRun

# 4. 헬스체크
curl http://localhost:8080/actuator/health
```

### Docker Compose 구성

```yaml
version: '3.8'

services:
  mariadb:
    image: mariadb:11
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: payment_service
    ports:
      - "3306:3306"
    volumes:
      - mariadb-data:/var/lib/mysql

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

volumes:
  mariadb-data:
```

---

## 프로젝트 분석

### 아키텍처 강점

**1. 의존성 역전을 통한 테스트 용이성**

Port 인터페이스를 통해 외부 의존성(Toss API, 예약 서비스)을 Mock으로 대체 가능합니다. 이는 단위 테스트 속도를 크게 향상시키고, 외부 서비스 장애와 무관하게 테스트를 실행할 수 있게 합니다.

**2. Outbox Pattern으로 이벤트 신뢰성 보장**

금융 도메인에서 가장 중요한 신뢰성을 Outbox Pattern으로 확보했습니다. DB 커밋과 이벤트 발행의 원자성이 보장되어 결제 완료 후 예약 서비스에 반드시 알림이 전달됩니다.

**3. Fail-Fast 검증으로 불필요한 처리 방지**

예약 금액 검증을 Toss API 호출 전에 수행하여 잘못된 요청을 조기에 차단합니다. 이는 외부 API 호출 비용을 절감하고 빠른 피드백을 제공합니다.

**4. Factory Pattern으로 불변성 보장**

Payment와 Refund 객체를 생성자가 아닌 Factory Method로만 생성하도록 강제하여 잘못된 상태의 객체 생성을 컴파일 타임에 방지합니다.

### 설계 결정 근거

**왜 Hexagonal Architecture를 선택했나?**

MSA 환경에서 외부 서비스(예약, Toss)와의 통합이 빈번합니다. Hexagonal Architecture는 이러한 외부 의존성을 Port로 추상화하여 변경에 유연하게 대응할 수 있습니다. 예를 들어, Toss
Payments를 다른 PG사로 교체할 때 Adapter만 변경하면 됩니다.

**왜 Outbox Pattern을 사용했나?**

Two-Phase Commit이나 Saga Pattern보다 구현이 단순하면서도 At-least-once 전달을 보장합니다. 금융 도메인에서는 이벤트 유실이 치명적이므로 약간의 복잡도 증가는 감수할 만한 가치가
있습니다.

**왜 동기 호출로 예약 검증을 하나?**

비동기 검증은 사용자 경험을 저해합니다. 결제 시도 후 몇 초 뒤에 "금액이 잘못되었습니다"라는 메시지를 받는 것은 좋지 않습니다. 동기 호출은 즉시 피드백을 제공하여 UX를 개선합니다.

---

## 향후 개선사항

### 단기 (1~2주)

**1. Circuit Breaker 추가**

예약 서비스 장애 시 빠른 실패 처리를 위해 Resilience4j Circuit Breaker 도입

```java
@CircuitBreaker(name = "reservation-service", fallbackMethod = "reservationFallback")
public ReservationInfo getReservation(String reservationId) {
    return reservationClient.getReservation(reservationId);
}
```

**2. 멱등성 키 만료 정책**

오래된 멱등성 키를 정리하여 DB 용량 관리

```sql
DELETE FROM payments
WHERE idempotency_key IS NOT NULL
  AND created_at < NOW() - INTERVAL 7 DAY;
```

**3. 결제 타임아웃 설정**

Toss API 호출 시 적절한 타임아웃으로 무한 대기 방지

---

### 중기 (1~2개월)

**1. 결제 재시도 메커니즘**

네트워크 일시 장애 시 자동 재시도

```java
@Retryable(
    value = TossApiException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000)
)
public TossPaymentResponse approve(Payment payment) {
    return tossClient.approve(payment);
}
```

**2. 결제 통계 API**

일별/주별/월별 결제 통계 제공으로 비즈니스 인사이트 확보

**3. 환불 이벤트 발행**

환불 완료 시에도 Kafka 이벤트 발행하여 정산 서비스 연동

---

### 장기 (3개월 이상)

**1. CQRS 패턴 적용**

결제 조회를 별도 Read Model로 분리하여 조회 성능 최적화

**2. 다중 PG사 지원**

Toss 외 다른 PG사(카카오페이, 네이버페이) 추가 지원

```java
public interface PaymentGateway {
    PaymentResponse approve(Payment payment);
    RefundResponse refund(Refund refund);
}

@Component
public class TossPaymentGateway implements PaymentGateway { ... }

@Component
public class KakaoPayGateway implements PaymentGateway { ... }
```

**3. 부분 환불 지원**

전체 금액이 아닌 일부 금액만 환불하는 기능 추가

---

**마지막 업데이트**: 2025-11-20
**작성자**: TeamBind
**버전**: v0.0.1-SNAPSHOT
