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

    @Id
    @Column(name = "payment_id", length = 50)
    private String paymentId;

    @Column(name = "reservation_id", nullable = false, length = 50)
    private String reservationId;

    @Embedded
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "order_id", length = 100)
    private String orderId;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDateTime checkInDate;

    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

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
        if (checkInDate.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Check-in date는 현재 시각 이후여야 합니다");
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