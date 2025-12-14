# ADR 002: 이벤트 기반 결제 준비 (Event-Driven Payment Preparation)

**날짜**: 2025-11-21  
**상태**: 승인됨  
**결정자**: TeamBind

---

## 컨텍스트 및 문제 정의

MSA 환경에서 결제 서비스는 클라이언트의 결제 요청 시 금액을 어떻게 검증할 것인가?

### 문제점

1. 클라이언트가 토스 결제창에서 금액을 변조할 가능성
2. 결제 승인 시점에 예약 서비스를 동기 호출하면 레이턴시 증가
3. 예약 서비스 장애 시 결제 불가 (가용성 저하)

---

## 결정 사항

**예약 생성 시 Kafka 이벤트로 결제 정보를 사전 준비합니다.**

---

## 근거

### 플로우

```
1. [예약 서비스] 예약 생성 완료
   ↓
2. [예약 서비스] Kafka 이벤트 발행
   Topic: reservation-events
   {
     "eventType": "RESERVATION_PAYMENT_REQUIRED",
     "reservationId": "RSV-001",
     "amount": 50000
   }
   ↓
3. [결제 서비스] 이벤트 수신
   ↓
4. [결제 서비스] Payment PREPARED 상태로 저장
   ↓
5. [클라이언트] Toss 결제창 호출
   ↓
6. [결제 서비스] 결제 승인 API 호출
   - 사전 저장된 금액과 비교
   - 일치하면 승인, 불일치하면 즉시 거부
```

---

## 장점

1. **Fail-Fast 금액 검증**
	- 결제 승인 시 이미 저장된 금액과 비교
	- 예약 서비스 동기 호출 불필요 (레이턴시 감소)

2. **멱등성 자동 보장**
	- reservationId 기반으로 중복 결제 방지
	- Payment 테이블에 UNIQUE 제약

3. **결제 추적 가능**
	- 결제 시도 전부터 Payment 엔티티 존재
	- PREPARED → COMPLETED 전체 흐름 추적

---

## 단점 및 완화 전략

**단점 1: 이벤트 지연**

- Kafka 지연 시 Payment가 준비되지 않아 승인 실패 가능

**완화 전략:**

```java
// Fallback: Payment가 없으면 예약 서비스 동기 호출
Payment payment = paymentRepository
    .findByReservationId(orderId)
    .orElseGet(() -> {
        ReservationInfo reservation = reservationClient.getReservation(orderId);
        return Payment.prepare(reservation);
    });
```

---

**최종 승인**: 2025-11-21  
**승인자**: TeamBind
