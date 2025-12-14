# API 명세서

## 목차

- [개요](#개요)
- [공통 사항](#공통-사항)
- [결제 API](#결제-api)
- [환불 API](#환불-api)
- [조회 API](#조회-api)
- [에러 코드](#에러-코드)

---

## 개요

Toss Payment Service의 RESTful API 명세서입니다.

**Base URL**: `http://localhost:8080/api/v1`

**Content-Type**: `application/json`

**API 버전**: v1

---

## 공통 사항

### 공통 헤더

| 헤더명             | 필수 | 설명            | 예시                 |
|-----------------|----|---------------|--------------------|
| `Content-Type`  | ✅  | 요청 본문 형식      | `application/json` |
| `Accept`        | ✅  | 응답 본문 형식      | `application/json` |
| `X-Request-ID`  | ❌  | 요청 추적 ID      | `req-20251120-001` |
| `Authorization` | ❌  | 인증 토큰 (향후 추가) | `Bearer {token}`   |

### 공통 응답 형식

**성공 응답**

```json
{
  "data": {
    // 실제 데이터
  },
  "timestamp": "2025-11-20T14:30:00"
}
```

**에러 응답**

```json
{
  "errorCode": "PAYMENT_AMOUNT_MISMATCH",
  "message": "요청 금액과 실제 금액이 일치하지 않습니다",
  "details": {
    "requestedAmount": 50000,
    "actualAmount": 48000
  },
  "timestamp": "2025-11-20T14:30:00",
  "path": "/api/v1/payments"
}
```

### HTTP 상태 코드

| 상태 코드                     | 설명         | 사용 시나리오       |
|---------------------------|------------|---------------|
| 200 OK                    | 성공         | 조회 성공         |
| 201 Created               | 생성 성공      | 결제/환불 생성 성공   |
| 400 Bad Request           | 잘못된 요청     | 입력 검증 실패      |
| 404 Not Found             | 리소스 없음     | 존재하지 않는 결제 ID |
| 409 Conflict              | 충돌         | 금액 불일치, 중복 요청 |
| 422 Unprocessable Entity  | 비즈니스 규칙 위반 | 환불 불가 상태      |
| 500 Internal Server Error | 서버 오류      | 예상치 못한 오류     |
| 502 Bad Gateway           | 외부 서비스 오류  | Toss API 오류   |
| 503 Service Unavailable   | 서비스 불가     | 예약 서비스 장애     |

---

## 결제 API

### 1. 결제 준비

**Endpoint**: `POST /api/v1/payments`

**설명**: 새로운 결제를 준비합니다. 예약 정보를 기반으로 결제 객체를 생성하며, Kafka 이벤트 또는 직접 API 호출 모두 지원합니다 (Dual Path).

**Request Body**

```json
{
  "reservationId": "RSV-20251120-001",
  "amount": 50000,
  "checkInDate": "2025-11-25T15:00:00"
}
```

| 필드              | 타입     | 필수 | 설명        | 제약 조건              |
|-----------------|--------|----|-----------|--------------------|
| `reservationId` | String | ✅  | 예약 ID     | 필수                 |
| `amount`        | Number | ✅  | 결제 금액 (원) | 양수                 |
| `checkInDate`   | String | ✅  | 체크인 날짜/시각 | ISO-8601, 현재 또는 미래 |

**Response (201 Created)**

```json
{
  "paymentId": "PAY-1A2B3C4D",
  "reservationId": "RSV-20251120-001",
  "amount": 50000,
  "status": "PREPARED",
  "checkInDate": "2025-11-25T15:00:00",
  "idempotencyKey": "idempotency-key-auto-generated",
  "createdAt": "2025-11-20T14:30:00"
}
```

| 필드               | 타입     | 설명                  |
|------------------|--------|---------------------|
| `paymentId`      | String | 결제 ID (시스템 자동 생성)   |
| `reservationId`  | String | 예약 ID               |
| `amount`         | Number | 결제 금액               |
| `status`         | String | 결제 상태 (`PREPARED`)  |
| `checkInDate`    | String | 체크인 날짜 (환불 정책 계산용)  |
| `idempotencyKey` | String | 자동 생성된 멱등성 키        |
| `createdAt`      | String | 결제 생성 시각 (ISO-8601) |

---

### 2. 결제 승인

**Endpoint**: `POST /api/v1/payments/confirm`

**설명**: Toss Payments 위젯에서 받은 정보로 결제를 승인합니다.

**Request Body**

```json
{
  "paymentId": "PAY-1A2B3C4D",
  "orderId": "order-unique-id",
  "paymentKey": "toss-payment-key-123",
  "amount": 50000
}
```

| 필드           | 타입     | 필수 | 설명         | 제약 조건             |
|--------------|--------|----|------------|-------------------|
| `paymentId`  | String | ✅  | 결제 ID      | 필수                |
| `orderId`    | String | ✅  | Toss 주문 ID | 필수                |
| `paymentKey` | String | ✅  | Toss 결제 키  | 필수                |
| `amount`     | Number | ✅  | 결제 금액 (원)  | 양수, 준비된 금액과 일치 필요 |

**Response (200 OK)**

```json
{
  "paymentId": "PAY-1A2B3C4D",
  "reservationId": "RSV-20251120-001",
  "amount": 50000,
  "status": "COMPLETED",
  "orderId": "order-unique-id",
  "paymentKey": "toss-payment-key-123",
  "transactionId": "toss-transaction-id",
  "method": "CARD",
  "paidAt": "2025-11-20T14:30:00"
}
```

| 필드              | 타입     | 설명                                            |
|-----------------|--------|-----------------------------------------------|
| `paymentId`     | String | 결제 ID                                         |
| `reservationId` | String | 예약 ID                                         |
| `amount`        | Number | 결제 금액                                         |
| `status`        | String | 결제 상태 (`COMPLETED`)                           |
| `orderId`       | String | Toss 주문 ID                                    |
| `paymentKey`    | String | Toss 결제 키                                     |
| `transactionId` | String | Toss 거래 ID                                    |
| `method`        | String | 결제 수단 (`CARD`, `EASY_PAY`, `VIRTUAL_ACCOUNT`) |
| `paidAt`        | String | 결제 완료 시각 (ISO-8601)                           |

**Error Responses**

**400 Bad Request - 입력 검증 실패**

```json
{
  "errorCode": "INVALID_REQUEST",
  "message": "입력값이 유효하지 않습니다",
  "details": {
    "amount": "금액은 0보다 커야 합니다",
    "idempotencyKey": "멱등성 키는 필수입니다"
  },
  "timestamp": "2025-11-20T14:30:00",
  "path": "/api/v1/payments"
}
```

**404 Not Found - 예약 없음**

```json
{
  "errorCode": "RESERVATION_NOT_FOUND",
  "message": "예약을 찾을 수 없습니다",
  "details": {
    "reservationId": "RSV-20251120-001"
  },
  "timestamp": "2025-11-20T14:30:00",
  "path": "/api/v1/payments"
}
```

**409 Conflict - 금액 불일치**

```json
{
  "errorCode": "PAYMENT_AMOUNT_MISMATCH",
  "message": "요청 금액과 실제 금액이 일치하지 않습니다",
  "details": {
    "requestedAmount": 50000,
    "actualAmount": 48000
  },
  "timestamp": "2025-11-20T14:30:00",
  "path": "/api/v1/payments"
}
```

**409 Conflict - 중복 요청**

```json
{
  "errorCode": "DUPLICATE_PAYMENT",
  "message": "이미 처리된 결제 요청입니다",
  "details": {
    "idempotencyKey": "unique-key-12345",
    "existingPaymentId": "PAY-20251120-001"
  },
  "timestamp": "2025-11-20T14:30:00",
  "path": "/api/v1/payments"
}
```

**502 Bad Gateway - Toss API 오류**

```json
{
  "errorCode": "TOSS_API_ERROR",
  "message": "Toss Payments API 호출에 실패했습니다",
  "details": {
    "tossErrorCode": "INVALID_CARD",
    "tossMessage": "유효하지 않은 카드입니다"
  },
  "timestamp": "2025-11-20T14:30:00",
  "path": "/api/v1/payments"
}
```

**503 Service Unavailable - 예약 서비스 장애**

```json
{
  "errorCode": "RESERVATION_SERVICE_UNAVAILABLE",
  "message": "예약 서비스에 연결할 수 없습니다",
  "timestamp": "2025-11-20T14:30:00",
  "path": "/api/v1/payments"
}
```

**cURL 예시**

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "reservationId": "RSV-20251120-001",
    "amount": 50000,
    "paymentMethod": "CARD",
    "idempotencyKey": "unique-key-12345"
  }'
```

---

### 3. 결제 취소

**Endpoint**: `POST /api/v1/payments/{paymentId}/cancel`

**설명**: 승인된 결제를 취소합니다. 취소된 결제는 환불 프로세스를 거쳐 처리됩니다.

**Path Parameters**

| 파라미터        | 타입     | 필수 | 설명    |
|-------------|--------|----|-------|
| `paymentId` | String | ✅  | 결제 ID |

**Request Body**

```json
{
  "reason": "고객 요청에 의한 취소"
}
```

| 필드       | 타입     | 필수 | 설명    | 제약 조건 |
|----------|--------|----|-------|-------|
| `reason` | String | ✅  | 취소 사유 | 필수    |

**Response (200 OK)**

```json
{
  "paymentId": "PAY-1A2B3C4D",
  "reservationId": "RSV-20251120-001",
  "status": "CANCELLED",
  "cancelledAt": "2025-11-20T15:00:00"
}
```

| 필드              | 타입     | 설명                  |
|-----------------|--------|---------------------|
| `paymentId`     | String | 결제 ID               |
| `reservationId` | String | 예약 ID               |
| `status`        | String | 결제 상태 (`CANCELLED`) |
| `cancelledAt`   | String | 취소 시각 (ISO-8601)    |

**Error Responses**

**404 Not Found - 결제 없음**

```json
{
  "errorCode": "PAYMENT_NOT_FOUND",
  "message": "결제를 찾을 수 없습니다",
  "details": {
    "paymentId": "PAY-NOTFOUND"
  },
  "timestamp": "2025-11-20T15:00:00",
  "path": "/api/v1/payments/PAY-NOTFOUND/cancel"
}
```

**400 Bad Request - 취소 불가 상태**

```json
{
  "errorCode": "INVALID_PAYMENT_STATE",
  "message": "취소 가능한 상태가 아닙니다",
  "details": {
    "paymentId": "PAY-1A2B3C4D",
    "currentStatus": "CANCELLED",
    "reason": "이미 취소된 결제입니다"
  },
  "timestamp": "2025-11-20T15:00:00",
  "path": "/api/v1/payments/PAY-1A2B3C4D/cancel"
}
```

**cURL 예시**

```bash
curl -X POST http://localhost:8080/api/v1/payments/PAY-1A2B3C4D/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "고객 요청에 의한 취소"
  }'
```

---

### 4. 결제 조회 (단건)

**Endpoint**: `GET /api/v1/payments/{paymentId}`

**설명**: 결제 ID로 결제 정보를 조회합니다.

**Path Parameters**

| 파라미터        | 타입     | 필수 | 설명    |
|-------------|--------|----|-------|
| `paymentId` | String | ✅  | 결제 ID |

**Response (200 OK)**

```json
{
  "paymentId": "PAY-20251120-001",
  "reservationId": "RSV-20251120-001",
  "amount": 50000,
  "paymentMethod": "CARD",
  "status": "COMPLETED",
  "orderId": "TOSS-ORD-001",
  "paymentKey": "TOSS-KEY-001",
  "transactionId": "TXN-001",
  "checkInDate": "2025-11-25T15:00:00",
  "createdAt": "2025-11-20T14:25:00",
  "paidAt": "2025-11-20T14:30:00"
}
```

| 필드              | 타입     | 설명                                                    |
|-----------------|--------|-------------------------------------------------------|
| `paymentId`     | String | 결제 ID                                                 |
| `reservationId` | String | 예약 ID                                                 |
| `amount`        | Number | 결제 금액                                                 |
| `paymentMethod` | String | 결제 수단                                                 |
| `status`        | String | 결제 상태 (`PENDING`, `COMPLETED`, `FAILED`, `CANCELLED`) |
| `orderId`       | String | Toss 주문 ID                                            |
| `paymentKey`    | String | Toss 결제 키                                             |
| `transactionId` | String | Toss 거래 ID                                            |
| `checkInDate`   | String | 체크인 날짜 (환불 계산용)                                       |
| `createdAt`     | String | 결제 생성 시각                                              |
| `paidAt`        | String | 결제 완료 시각                                              |

**Error Response (404 Not Found)**

```json
{
  "errorCode": "PAYMENT_NOT_FOUND",
  "message": "결제를 찾을 수 없습니다",
  "details": {
    "paymentId": "PAY-20251120-999"
  },
  "timestamp": "2025-11-20T14:30:00",
  "path": "/api/v1/payments/PAY-20251120-999"
}
```

**cURL 예시**

```bash
curl -X GET http://localhost:8080/api/v1/payments/PAY-20251120-001
```

---

---

## 환불 API

### 1. 환불 요청

**Endpoint**: `POST /api/v1/refunds`

**설명**: 결제에 대한 환불을 요청합니다. 환불 금액은 환불 정책에 따라 자동 계산됩니다.

**Request Body**

```json
{
  "paymentId": "PAY-20251120-001",
  "reason": "고객 요청"
}
```

| 필드          | 타입     | 필수 | 설명    | 제약 조건   |
|-------------|--------|----|-------|---------|
| `paymentId` | String | ✅  | 결제 ID | 50자 이하  |
| `reason`    | String | ❌  | 환불 사유 | 500자 이하 |

**Response (201 Created)**

```json
{
  "refundId": "REF-20251120-001",
  "paymentId": "PAY-20251120-001",
  "originalAmount": 50000,
  "refundAmount": 50000,
  "refundRate": 1.00,
  "commissionFree": true,
  "status": "COMPLETED",
  "tossRefundKey": "TOSS-REF-001",
  "requestedAt": "2025-11-20T14:35:00",
  "completedAt": "2025-11-20T14:35:30"
}
```

| 필드               | 타입      | 설명                  |
|------------------|---------|---------------------|
| `refundId`       | String  | 환불 ID (시스템 생성)      |
| `paymentId`      | String  | 원본 결제 ID            |
| `originalAmount` | Number  | 원본 결제 금액            |
| `refundAmount`   | Number  | 실제 환불 금액            |
| `refundRate`     | Number  | 환불율 (0.0 ~ 1.0)     |
| `commissionFree` | Boolean | 수수료 면제 여부           |
| `status`         | String  | 환불 상태 (`COMPLETED`) |
| `tossRefundKey`  | String  | Toss 환불 키           |
| `requestedAt`    | String  | 환불 요청 시각            |
| `completedAt`    | String  | 환불 완료 시각            |

**환불율 계산 예시**

```json
// 5일 전 취소 (100% 환불)
{
  "originalAmount": 100000,
  "refundAmount": 100000,
  "refundRate": 1.00
}

// 4일 전 취소 (70% 환불)
{
  "originalAmount": 100000,
  "refundAmount": 70000,
  "refundRate": 0.70
}

// 결제 후 10분 이내 (100% 환불, 수수료 면제)
{
  "originalAmount": 100000,
  "refundAmount": 100000,
  "refundRate": 1.00,
  "commissionFree": true
}
```

**Error Responses**

**404 Not Found - 결제 없음**

```json
{
  "errorCode": "PAYMENT_NOT_FOUND",
  "message": "결제를 찾을 수 없습니다",
  "details": {
    "paymentId": "PAY-20251120-999"
  },
  "timestamp": "2025-11-20T14:35:00",
  "path": "/api/v1/refunds"
}
```

**422 Unprocessable Entity - 환불 불가 상태**

```json
{
  "errorCode": "INVALID_PAYMENT_STATE",
  "message": "환불 가능한 상태가 아닙니다",
  "details": {
    "paymentId": "PAY-20251120-001",
    "currentStatus": "FAILED",
    "requiredStatus": "COMPLETED"
  },
  "timestamp": "2025-11-20T14:35:00",
  "path": "/api/v1/refunds"
}
```

**422 Unprocessable Entity - 환불 기한 초과**

```json
{
  "errorCode": "REFUND_NOT_ALLOWED",
  "message": "환불 가능 기간이 지났습니다",
  "details": {
    "checkInDate": "2025-11-20T15:00:00",
    "currentTime": "2025-11-20T16:00:00",
    "reason": "체크인 날짜 이후에는 환불이 불가능합니다"
  },
  "timestamp": "2025-11-20T16:00:00",
  "path": "/api/v1/refunds"
}
```

**422 Unprocessable Entity - 이미 환불됨**

```json
{
  "errorCode": "ALREADY_REFUNDED",
  "message": "이미 환불 처리된 결제입니다",
  "details": {
    "paymentId": "PAY-20251120-001",
    "refundId": "REF-20251120-001"
  },
  "timestamp": "2025-11-20T14:35:00",
  "path": "/api/v1/refunds"
}
```

**cURL 예시**

```bash
curl -X POST http://localhost:8080/api/v1/refunds \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId": "PAY-20251120-001",
    "reason": "고객 요청"
  }'
```

---

### 2. 환불 조회 (단건)

**Endpoint**: `GET /api/v1/refunds/{refundId}`

**설명**: 환불 ID로 환불 정보를 조회합니다.

**Path Parameters**

| 파라미터       | 타입     | 필수 | 설명    |
|------------|--------|----|-------|
| `refundId` | String | ✅  | 환불 ID |

**Response (200 OK)**

```json
{
  "refundId": "REF-20251120-001",
  "paymentId": "PAY-20251120-001",
  "originalAmount": 50000,
  "refundAmount": 50000,
  "refundRate": 1.00,
  "commissionFree": true,
  "status": "COMPLETED",
  "tossRefundKey": "TOSS-REF-001",
  "requestedAt": "2025-11-20T14:35:00",
  "completedAt": "2025-11-20T14:35:30"
}
```

**cURL 예시**

```bash
curl -X GET http://localhost:8080/api/v1/refunds/REF-20251120-001
```

---

### 3. 결제별 환불 조회

**Endpoint**: `GET /api/v1/refunds?paymentId={paymentId}`

**설명**: 결제 ID로 환불 목록을 조회합니다.

**Query Parameters**

| 파라미터        | 타입     | 필수 | 설명    |
|-------------|--------|----|-------|
| `paymentId` | String | ✅  | 결제 ID |

**Response (200 OK)**

```json
{
  "refunds": [
    {
      "refundId": "REF-20251120-001",
      "refundAmount": 50000,
      "refundRate": 1.00,
      "status": "COMPLETED",
      "requestedAt": "2025-11-20T14:35:00",
      "completedAt": "2025-11-20T14:35:30"
    }
  ],
  "totalCount": 1
}
```

**cURL 예시**

```bash
curl -X GET "http://localhost:8080/api/v1/refunds?paymentId=PAY-20251120-001"
```

---

## 조회 API

### 1. 헬스체크

**Endpoint**: `GET /actuator/health`

**설명**: 서비스 상태를 확인합니다.

**Response (200 OK)**

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MariaDB",
        "validationQuery": "isValid()"
      }
    },
    "kafka": {
      "status": "UP"
    }
  }
}
```

**cURL 예시**

```bash
curl -X GET http://localhost:8080/actuator/health
```

---

## 에러 코드

### 결제 관련

| 에러 코드                     | HTTP 상태 | 설명               | 해결 방법          |
|---------------------------|---------|------------------|----------------|
| `PAYMENT_NOT_FOUND`       | 404     | 결제를 찾을 수 없음      | 올바른 결제 ID 확인   |
| `PAYMENT_AMOUNT_MISMATCH` | 409     | 요청 금액과 실제 금액 불일치 | 예약 금액 재확인      |
| `DUPLICATE_PAYMENT`       | 409     | 중복 결제 요청         | 다른 멱등성 키 사용    |
| `INVALID_PAYMENT_STATE`   | 422     | 잘못된 결제 상태        | 결제 상태 확인 후 재시도 |
| `TOSS_API_ERROR`          | 502     | Toss API 오류      | Toss 에러 메시지 확인 |

### 환불 관련

| 에러 코드                  | HTTP 상태 | 설명          | 해결 방법        |
|------------------------|---------|-------------|--------------|
| `REFUND_NOT_FOUND`     | 404     | 환불을 찾을 수 없음 | 올바른 환불 ID 확인 |
| `REFUND_NOT_ALLOWED`   | 422     | 환불 불가       | 환불 가능 기간 확인  |
| `ALREADY_REFUNDED`     | 422     | 이미 환불됨      | 환불 내역 확인     |
| `INVALID_REFUND_STATE` | 422     | 잘못된 환불 상태   | 환불 상태 확인     |

### 예약 관련

| 에러 코드                             | HTTP 상태 | 설명          | 해결 방법        |
|-----------------------------------|---------|-------------|--------------|
| `RESERVATION_NOT_FOUND`           | 404     | 예약을 찾을 수 없음 | 올바른 예약 ID 확인 |
| `RESERVATION_SERVICE_UNAVAILABLE` | 503     | 예약 서비스 장애   | 잠시 후 재시도     |

### 공통

| 에러 코드                   | HTTP 상태 | 설명     | 해결 방법  |
|-------------------------|---------|--------|--------|
| `INVALID_REQUEST`       | 400     | 잘못된 요청 | 입력값 검증 |
| `UNAUTHORIZED`          | 401     | 인증 실패  | 토큰 확인  |
| `FORBIDDEN`             | 403     | 권한 없음  | 권한 확인  |
| `INTERNAL_SERVER_ERROR` | 500     | 서버 오류  | 관리자 문의 |

---

## API 사용 예시

### 결제 → 조회 시나리오

```bash
# 1. 결제 요청
PAYMENT_RESPONSE=$(curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "reservationId": "RSV-20251120-001",
    "amount": 50000,
    "paymentMethod": "CARD",
    "idempotencyKey": "unique-key-12345"
  }')

# 2. 결제 ID 추출
PAYMENT_ID=$(echo $PAYMENT_RESPONSE | jq -r '.paymentId')

# 3. 결제 조회
curl -X GET http://localhost:8080/api/v1/payments/$PAYMENT_ID
```

### 환불 시나리오

```bash
# 1. 환불 요청
REFUND_RESPONSE=$(curl -X POST http://localhost:8080/api/v1/refunds \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId": "PAY-20251120-001",
    "reason": "고객 요청"
  }')

# 2. 환불 ID 추출
REFUND_ID=$(echo $REFUND_RESPONSE | jq -r '.refundId')

# 3. 환불 조회
curl -X GET http://localhost:8080/api/v1/refunds/$REFUND_ID
```

---

**마지막 업데이트**: 2025-11-20
**작성자**: TeamBind
**API 버전**: v1
