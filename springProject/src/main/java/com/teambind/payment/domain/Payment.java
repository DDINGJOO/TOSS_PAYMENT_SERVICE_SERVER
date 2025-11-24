package com.teambind.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    // 결제 ID (기본키)
    @Id
    @Column(name = "payment_id", length = 50)
    private String paymentId;

    // 예약 ID (Dual Path 멱등성 보장을 위한 unique 제약)
    @Column(name = "reservation_id", nullable = false, length = 50, unique = true)
    private String reservationId;

    // 결제 금액
    @Embedded
    private Money amount;

    // 결제 수단 (카드, 가상계좌, 간편결제)
    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 20)
    private PaymentMethod method;

    // 결제 상태 (준비, 완료, 실패, 취소)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    // 주문 ID (클라이언트로부터 받은 값)
    @Column(name = "order_id", length = 100)
    private String orderId;

    // 결제 키 (토스로부터 받은 고유 식별자)
    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    // 거래 ID (토스 결제 승인 후 받은 거래 번호)
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    // 체크인 날짜 (환불 가능 여부 판단 기준)
    @Column(name = "check_in_date", nullable = false)
    private LocalDateTime checkInDate;

    // 멱등성 키 (중복 결제 방지)
    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    // 결제 생성 시각
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 결제 완료 시각
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // 결제 취소 시각
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // 결제 실패 사유
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Builder
    private Payment(String paymentId, String reservationId, Money amount, PaymentMethod method,
                    PaymentStatus status, String orderId, String paymentKey, String transactionId,
                    LocalDateTime checkInDate, String idempotencyKey, LocalDateTime createdAt,
                    LocalDateTime paidAt, LocalDateTime cancelledAt, String failureReason) {
        this.paymentId = paymentId;
        this.reservationId = reservationId;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.transactionId = transactionId;
        this.checkInDate = checkInDate;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
        this.cancelledAt = cancelledAt;
        this.failureReason = failureReason;
    }

    public static Payment prepare(String reservationId, Money amount, LocalDateTime checkInDate) {
        validateReservationId(reservationId);
        validateAmountNotNull(amount);
        validateCheckInDate(checkInDate);

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

    public void complete(String orderId, String paymentKey, String transactionId, PaymentMethod method) {
        validatePreparedStatus();
        validateOrderId(orderId);
        validatePaymentKey(paymentKey);

        this.status = PaymentStatus.COMPLETED;
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.transactionId = transactionId;
        this.method = method;
        this.paidAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        if (this.status == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 결제는 실패 처리할 수 없습니다");
        }

        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void cancel() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("완료된 결제만 취소할 수 있습니다. 현재 상태: " + this.status);
        }

        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void validateRefundable() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("완료된 결제만 환불 가능합니다. 현재 상태: " + this.status);
        }

        if (LocalDateTime.now().isAfter(this.checkInDate)) {
            throw new IllegalStateException("체크인 날짜 이후에는 환불이 불가능합니다");
        }
    }

    public void validateAmount(Money requestedAmount) {
        if (!this.amount.equals(requestedAmount)) {
            throw new IllegalArgumentException(
                    String.format("금액이 일치하지 않습니다. 저장된 금액: %s, 요청 금액: %s",
                            this.amount, requestedAmount)
            );
        }
    }

    private void validatePreparedStatus() {
        if (this.status != PaymentStatus.PREPARED) {
            throw new IllegalStateException("PREPARED 상태에서만 완료 처리 가능합니다. 현재 상태: " + this.status);
        }
    }

    private static void validateReservationId(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("Reservation ID는 필수입니다");
        }
    }

    private static void validateAmountNotNull(Money amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount는 필수입니다");
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount는 0보다 커야 합니다");
        }
    }

    private static void validateCheckInDate(LocalDateTime checkInDate) {
        if (checkInDate == null) {
            throw new IllegalArgumentException("Check-in date는 필수입니다");
        }
        // 당일 예약을 허용하기 위해 약간의 tolerance를 둠 (5초)
        if (checkInDate.isBefore(LocalDateTime.now().minusSeconds(5))) {
            throw new IllegalArgumentException("Check-in date는 과거일 수 없습니다");
        }
    }

    private static void validateOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID는 필수입니다");
        }
    }

    private static void validatePaymentKey(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("Payment Key는 필수입니다");
        }
    }

    private static String generatePaymentId() {
        return "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String generateIdempotencyKey(String reservationId) {
        return "IDEM-" + reservationId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}