
# 예외 처리 표준화

> Version: v0.0.1-SNAPSHOT | Java 21 | Spring Boot 3.5.7

## 개요

Toss Payment Service는 공통 모듈의 예외 처리 인프라를 활용하여 도메인별 예외를 표준화합니다. 이를 통해 일관된 에러 응답 구조와 명확한 에러 분류를 제공합니다.

## 예외 처리 아키텍처

### 1. 계층 구조

```
CustomException (추상 기본 클래스)
    ├─ PaymentException (결제 도메인)
    ├─ RefundException (환불 도메인)
    ├─ TossApiException (외부 API)
    ├─ InvalidRequestException (요청 검증)
    └─ UnauthorizedException (인증/인가)
```

### 2. 핵심 컴포넌트

#### ErrorCode (Enum)
모든 에러 코드와 HTTP 상태를 중앙 집중 관리

```java
public enum ErrorCode {
    // 결제 관련 에러 (PAYMENT_0XX)
    PAYMENT_NOT_FOUND("PAYMENT_001", "Payment not found", HttpStatus.NOT_FOUND),
    PAYMENT_ALREADY_COMPLETED("PAYMENT_002", "Payment already completed", HttpStatus.CONFLICT),
    PAYMENT_ALREADY_CANCELLED("PAYMENT_003", "Payment already cancelled", HttpStatus.CONFLICT),
    PAYMENT_AMOUNT_MISMATCH("PAYMENT_004", "Payment amount mismatch", HttpStatus.BAD_REQUEST),
    PAYMENT_NOT_COMPLETED("PAYMENT_005", "Payment not completed", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_STATUS("PAYMENT_006", "Invalid payment status", HttpStatus.BAD_REQUEST),
    PAYMENT_CONFIRMATION_FAILED("PAYMENT_007", "Payment confirmation failed", HttpStatus.BAD_GATEWAY),

    // 환불 관련 에러 (REFUND_0XX)
    REFUND_NOT_FOUND("REFUND_001", "Refund not found", HttpStatus.NOT_FOUND),
    REFUND_NOT_ALLOWED("REFUND_002", "Refund not allowed", HttpStatus.BAD_REQUEST),
    REFUND_AMOUNT_EXCEEDED("REFUND_003", "Refund amount exceeded", HttpStatus.BAD_REQUEST),
    REFUND_ALREADY_PROCESSED("REFUND_004", "Refund already processed", HttpStatus.CONFLICT),
    REFUND_PERIOD_EXPIRED("REFUND_005", "Refund period expired", HttpStatus.BAD_REQUEST),
    REFUND_PROCESSING_FAILED("REFUND_006", "Refund processing failed", HttpStatus.BAD_GATEWAY),

    // 외부 API 에러 (TOSS_0XX)
    TOSS_API_ERROR("TOSS_001", "Toss API error", HttpStatus.BAD_GATEWAY),
    TOSS_API_TIMEOUT("TOSS_002", "Toss API timeout", HttpStatus.GATEWAY_TIMEOUT),
    TOSS_INVALID_RESPONSE("TOSS_003", "Invalid Toss API response", HttpStatus.BAD_GATEWAY);
}
```

#### CustomException (추상 클래스)
모든 커스텀 예외의 기본 클래스

```java
public abstract class CustomException extends RuntimeException {
    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public abstract String getExceptionType();
}
```

#### ErrorResponse (DTO)
표준화된 에러 응답 구조

```java
{
    "timestamp": "2025-11-23T10:30:00",
    "status": 404,
    "code": "PAYMENT_001",
    "message": "Payment not found: PAY-ABC12345",
    "path": "/api/v1/payments/PAY-ABC12345",
    "exceptionType": "PAYMENT_DOMAIN",
    "errors": []  // 필드 검증 에러 시에만 포함
}
```

## 도메인 예외 클래스

### PaymentException

**위치**: `com.teambind.payment.common.exception.PaymentException`

**용도**: 결제 도메인 로직 내에서 발생하는 예외

**팩토리 메서드**:
```java
// 결제를 찾을 수 없음
PaymentException.notFound(String paymentId)

// 이미 완료된 결제
PaymentException.alreadyCompleted(String paymentId)

// 이미 취소된 결제
PaymentException.alreadyCancelled(String paymentId)

// 금액 불일치
PaymentException.amountMismatch(Long expected, Long actual)

// 완료되지 않은 결제
PaymentException.notCompleted(String paymentId)

// 잘못된 결제 상태
PaymentException.invalidStatus(String paymentId, PaymentStatus current, PaymentStatus expected)
```

**사용 예시**:
```java
// PaymentConfirmService.java
Payment payment = paymentRepository.findById(paymentId)
    .orElseThrow(() -> PaymentException.notFound(paymentId));
```

### RefundException

**위치**: `com.teambind.payment.common.exception.RefundException`

**용도**: 환불 도메인 로직 내에서 발생하는 예외

**팩토리 메서드**:
```java
// 환불을 찾을 수 없음
RefundException.notFound(String refundId)

// 환불이 허용되지 않음
RefundException.notAllowed(String reason)

// 환불 금액 초과
RefundException.amountExceeded(Money refundAmount, Money originalAmount)

// 이미 처리된 환불
RefundException.alreadyProcessed(String refundId)

// 환불 기간 만료
RefundException.periodExpired(String paymentId, LocalDateTime checkInDate)
```

**사용 예시**:
```java
// RefundService.java
} catch (Exception e) {
    refund.fail(e.getMessage());
    refundRepository.save(refund);
    throw new RefundException(
        ErrorCode.REFUND_PROCESSING_FAILED,
        "Refund processing failed for payment: " + paymentId,
        e
    );
}
```

### TossApiException

**위치**: `com.teambind.payment.common.exception.TossApiException`

**용도**: Toss Payments API 호출 중 발생하는 예외

**팩토리 메서드**:
```java
// API 에러
TossApiException.apiError(String message)

// 타임아웃
TossApiException.timeout()

// 잘못된 응답
TossApiException.invalidResponse(String message)
```

**사용 예시**:
```java
// PaymentCancelService.java
} catch (Exception e) {
    log.error("결제 취소 실패 - paymentId: {}", paymentId, e);
    payment.fail("결제 취소 실패: " + e.getMessage());
    paymentRepository.save(payment);
    throw new TossApiException(
        ErrorCode.TOSS_API_ERROR,
        "Payment cancellation failed for: " + paymentId,
        e
    );
}
```

## 글로벌 예외 핸들러

### GlobalExceptionHandler

**위치**: `com.teambind.common.exceptions.GlobalExceptionHandler`

**기능**:
- `@RestControllerAdvice`를 사용한 중앙 집중식 예외 처리
- CustomException 자동 변환
- 검증 에러 처리
- 예상치 못한 에러 처리

**처리 메서드**:
```java
@ExceptionHandler(CustomException.class)
public ResponseEntity<ErrorResponse> handleCustomException(
    CustomException ex, HttpServletRequest request)

@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidationException(
    MethodArgumentNotValidException ex, HttpServletRequest request)

@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleGlobalException(
    Exception ex, HttpServletRequest request)
```

## 예외 처리 플로우

### 1. 정상 플로우

```
Service Layer
    └─> Repository.findById()
        └─> Optional.orElseThrow(() -> PaymentException.notFound(id))
            └─> GlobalExceptionHandler.handleCustomException()
                └─> ErrorResponse 생성
                    └─> HTTP 404 응답
```

### 2. 외부 API 호출 플로우

```
Service Layer
    └─> TossPaymentClient.confirmPayment()
        └─> HTTP 오류 발생
            └─> catch (Exception e)
                └─> throw TossApiException.apiError(message)
                    └─> GlobalExceptionHandler.handleCustomException()
                        └─> ErrorResponse 생성
                            └─> HTTP 502 응답
```

### 3. 검증 에러 플로우

```
Controller
    └─> @Valid @RequestBody
        └─> 검증 실패
            └─> MethodArgumentNotValidException
                └─> GlobalExceptionHandler.handleValidationException()
                    └─> ErrorResponse (with field errors)
                        └─> HTTP 400 응답
```

## 예외 처리 모범 사례

### 1. 도메인 예외 사용
```java
// ❌ 나쁜 예
if (payment == null) {
    throw new IllegalArgumentException("결제를 찾을 수 없습니다");
}

// ✅ 좋은 예
Payment payment = paymentRepository.findById(paymentId)
    .orElseThrow(() -> PaymentException.notFound(paymentId));
```

### 2. 팩토리 메서드 활용
```java
// ❌ 나쁜 예
throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
    "금액이 일치하지 않습니다");

// ✅ 좋은 예
throw PaymentException.amountMismatch(expected, actual);
```

### 3. 원인 예외 포함
```java
// ❌ 나쁜 예
} catch (Exception e) {
    throw new TossApiException(ErrorCode.TOSS_API_ERROR, "API 오류");
}

// ✅ 좋은 예
} catch (Exception e) {
    throw new TossApiException(
        ErrorCode.TOSS_API_ERROR,
        "Payment confirmation failed: " + e.getMessage(),
        e  // 원인 예외 포함
    );
}
```

### 4. 로깅과 함께 사용
```java
// ✅ 좋은 예
try {
    // 비즈니스 로직
} catch (Exception e) {
    log.error("환불 처리 실패 - paymentId: {}, error: {}",
        paymentId, e.getMessage(), e);
    refund.fail(e.getMessage());
    refundRepository.save(refund);
    throw new RefundException(
        ErrorCode.REFUND_PROCESSING_FAILED,
        "Refund processing failed for payment: " + paymentId,
        e
    );
}
```

## 테스트 작성

### 예외 발생 테스트
```java
@Test
@DisplayName("결제 승인 실패 - 결제 정보를 찾을 수 없음")
void confirmPayment_fail_paymentNotFound() {
    // given
    String paymentId = "non-existent-id";
    given(paymentRepository.findById(paymentId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> paymentConfirmService.confirmPayment(
            paymentId, "order-123", "payment-key-123", 100000L
    ))
            .isInstanceOf(PaymentException.class)
            .hasMessageContaining("Payment not found");
}
```

## 에러 코드 네이밍 규칙

### 규칙
- **형식**: `{DOMAIN}_{숫자 3자리}`
- **도메인**: PAYMENT, REFUND, TOSS, VALIDATION 등
- **숫자**: 001부터 시작하여 순차 증가

### 예시
```
PAYMENT_001  - Payment not found
PAYMENT_002  - Payment already completed
REFUND_001   - Refund not found
TOSS_001     - Toss API error
```

## HTTP 상태 코드 매핑

| ErrorCode | HTTP Status | 설명 |
|-----------|-------------|------|
| PAYMENT_NOT_FOUND | 404 | 리소스를 찾을 수 없음 |
| PAYMENT_ALREADY_COMPLETED | 409 | 상태 충돌 |
| PAYMENT_AMOUNT_MISMATCH | 400 | 잘못된 요청 |
| PAYMENT_CONFIRMATION_FAILED | 502 | 외부 API 오류 |
| REFUND_NOT_ALLOWED | 400 | 비즈니스 규칙 위반 |
| TOSS_API_TIMEOUT | 504 | 게이트웨이 타임아웃 |

## 확장 가이드

### 새로운 에러 코드 추가

1. **ErrorCode.java에 추가**
```java
// 새로운 도메인 에러
PAYMENT_EXPIRED("PAYMENT_008", "Payment expired", HttpStatus.GONE),
```

2. **팩토리 메서드 추가** (선택사항)
```java
// PaymentException.java
public static PaymentException expired(String paymentId, LocalDateTime expiredAt) {
    return new PaymentException(
        ErrorCode.PAYMENT_EXPIRED,
        String.format("Payment expired: %s at %s", paymentId, expiredAt)
    );
}
```

### 새로운 예외 클래스 추가

```java
public class ReservationException extends CustomException {

    public ReservationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public String getExceptionType() {
        return "RESERVATION_DOMAIN";
    }

    public static ReservationException notFound(String reservationId) {
        return new ReservationException(
            ErrorCode.RESERVATION_NOT_FOUND,
            "Reservation not found: " + reservationId
        );
    }
}
```

## 관련 문서

- [ADR-006: Exception Handling Standardization](../adr/006-exception-handling-standardization.md)
- [Domain Model](DOMAIN_MODEL.md)
- [API Specification](../API_SPECIFICATION.md)

---

**최종 업데이트**: 2025-11-23
**작성자**: TeamBind
**PR**: #33
