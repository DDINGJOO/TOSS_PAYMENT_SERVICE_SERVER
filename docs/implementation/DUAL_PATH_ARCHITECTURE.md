# Dual Path Architecture

## 개요

Toss Payment Service는 결제 준비 요청을 **두 가지 경로**로 처리할 수 있는 Dual Path 아키텍처를 채택했습니다.

## 두 가지 경로

### 1. REST API 경로
```
Frontend → POST /api/v1/payments → PaymentController → PaymentPrepareService → DB
```

### 2. Kafka Event 경로
```
Reservation Service → Kafka Topic → ReservationEventConsumer → PaymentPrepareService → DB
```

## 왜 Dual Path인가?

### 장점

1. **빠른 테스트**: Kafka 인프라 없이도 API로 직접 테스트 가능
2. **이벤트 지연 대응**: Kafka 지연 시 API로 먼저 처리 가능
3. **유연성**: 클라이언트가 원하는 방식 선택 가능
4. **장애 격리**: 한 경로 장애 시 다른 경로로 우회 가능

### 단점

1. **중복 처리 위험**: 동시 요청 시 Race Condition 발생 가능
2. **복잡도 증가**: 두 경로를 모두 관리해야 함

## Race Condition 방지

### 문제 시나리오

```
시간 | API 요청                          | Kafka 이벤트
-----|----------------------------------|----------------------------------
T1   | findByReservationId() → 없음      |
T2   |                                  | findByReservationId() → 없음
T3   | save() → Payment 생성             |
T4   |                                  | save() → 중복 생성! ❌
```

### 해결 방법

#### 1. DB Unique 제약조건

```java
@Column(name = "reservation_id", nullable = false, length = 50, unique = true)
private String reservationId;
```

#### 2. Exception Handling

```java
@Transactional
public Payment preparePayment(String reservationId, Long amount, LocalDateTime checkInDate) {
    return paymentRepository.findByReservationId(reservationId)
        .map(existingPayment -> {
            log.info("이미 처리된 예약입니다");
            return existingPayment;
        })
        .orElseGet(() -> {
            try {
                Payment payment = Payment.prepare(reservationId, Money.of(amount), checkInDate);
                return paymentRepository.save(payment);
            } catch (DataIntegrityViolationException e) {
                // Race Condition 발생 → 재조회
                log.warn("동시 요청 감지 - reservationId: {}", reservationId);
                return paymentRepository.findByReservationId(reservationId)
                    .orElseThrow(() -> new IllegalStateException("재조회 실패"));
            }
        });
}
```

### 동작 흐름

```
API 요청 (T1)                      Kafka 이벤트 (T2)
    |                                    |
    ├─ findByReservationId()             |
    |  └─ Empty                          |
    |                                    ├─ findByReservationId()
    |                                    |  └─ Empty
    ├─ save()                            |
    |  └─ Payment 생성 ✅                |
    |                                    ├─ save()
    |                                    |  └─ DataIntegrityViolationException ❌
    |                                    |
    |                                    ├─ catch 블록
    |                                    └─ findByReservationId()
    |                                       └─ 기존 Payment 반환 ✅
```

## 멱등성 보장

### 3단계 방어선

1. **Application Level**: `findByReservationId()` 선조회
2. **Database Level**: `unique` 제약조건
3. **Exception Handling**: `DataIntegrityViolationException` 재조회

## 성능 고려사항

### 일반적인 경우 (99%)
- 한 경로만 사용
- 단순 조회 또는 저장
- 성능 영향 없음

### Race Condition 발생 시 (1%)
- DB unique 제약 위반 감지
- 재조회 1회 추가
- 약간의 지연 발생 (무시할 수준)

## 모니터링

Race Condition 발생 빈도를 모니터링하기 위한 로그:

```java
log.warn("동시 요청 감지 - reservationId: {}. 기존 결제 정보 조회 중...", reservationId);
```

이 로그가 자주 발생한다면:
- API와 이벤트 처리 시간 조정 검토
- 분산 락 도입 고려

## 테스트 전략

### 단위 테스트
- 각 경로 독립적으로 테스트
- Exception handling 테스트

### 통합 테스트
- API 먼저 → 이벤트 나중
- 이벤트 먼저 → API 나중
- 동시 요청 시뮬레이션

## 결론

Dual Path 아키텍처는 유연성과 안정성을 제공하지만, Race Condition을 적절히 처리해야 합니다.

현재 구현은:
- ✅ DB 제약조건으로 데이터 무결성 보장
- ✅ Exception handling으로 사용자 경험 보장
- ✅ 로깅으로 모니터링 지원

---

**마지막 업데이트**: 2025-11-24
**작성자**: TeamBind