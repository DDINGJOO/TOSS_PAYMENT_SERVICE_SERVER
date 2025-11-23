package com.teambind.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    @Column(name = "refund_id", length = 50)
    private String refundId;

    @Column(name = "payment_id", nullable = false, length = 50)
    private String paymentId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "refund_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "refund_currency"))
    })
    private Money refundAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "original_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "original_currency"))
    })
    private Money originalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Builder
    private Refund(String refundId, String paymentId, Money refundAmount, Money originalAmount,
                   RefundStatus status, String reason, String cancelReason, String transactionId,
                   LocalDateTime requestedAt, LocalDateTime approvedAt, LocalDateTime completedAt,
                   String failureReason) {
        this.refundId = refundId;
        this.paymentId = paymentId;
        this.refundAmount = refundAmount;
        this.originalAmount = originalAmount;
        this.status = status;
        this.reason = reason;
        this.cancelReason = cancelReason;
        this.transactionId = transactionId;
        this.requestedAt = requestedAt;
        this.approvedAt = approvedAt;
        this.completedAt = completedAt;
        this.failureReason = failureReason;
    }

    public static Refund request(String paymentId, Money originalAmount, Money refundAmount, String reason) {
        validatePaymentId(paymentId);
        validateOriginalAmount(originalAmount);
        validateRefundAmount(refundAmount, originalAmount);
        validateReason(reason);

        return Refund.builder()
                .refundId(generateRefundId())
                .paymentId(paymentId)
                .originalAmount(originalAmount)
                .refundAmount(refundAmount)
                .status(RefundStatus.PENDING)
                .reason(reason)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    public void approve() {
        if (this.status != RefundStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 승인 가능합니다. 현재 상태: " + this.status);
        }

        this.status = RefundStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
    }

    public void complete(String transactionId) {
        if (this.status != RefundStatus.APPROVED) {
            throw new IllegalStateException("APPROVED 상태에서만 완료 처리 가능합니다. 현재 상태: " + this.status);
        }

        validateTransactionId(transactionId);

        this.status = RefundStatus.COMPLETED;
        this.transactionId = transactionId;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String failureReason) {
        if (this.status == RefundStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 환불은 실패 처리할 수 없습니다");
        }

        validateFailureReason(failureReason);

        this.status = RefundStatus.FAILED;
        this.failureReason = failureReason;
    }

    public void validateRefundAmount(Money calculatedAmount) {
        if (!this.refundAmount.equals(calculatedAmount)) {
            throw new IllegalArgumentException(
                    String.format("환불 금액이 일치하지 않습니다. 저장된 금액: %s, 계산된 금액: %s",
                            this.refundAmount, calculatedAmount)
            );
        }
    }

    public boolean isCompleted() {
        return this.status == RefundStatus.COMPLETED;
    }

    public boolean isPending() {
        return this.status == RefundStatus.PENDING;
    }

    public boolean isApproved() {
        return this.status == RefundStatus.APPROVED;
    }

    private static void validatePaymentId(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("Payment ID는 필수입니다");
        }
    }

    private static void validateOriginalAmount(Money originalAmount) {
        if (originalAmount == null) {
            throw new IllegalArgumentException("Original amount는 필수입니다");
        }
        if (!originalAmount.isPositive()) {
            throw new IllegalArgumentException("Original amount는 0보다 커야 합니다");
        }
    }

    private static void validateRefundAmount(Money refundAmount, Money originalAmount) {
        if (refundAmount == null) {
            throw new IllegalArgumentException("Refund amount는 필수입니다");
        }
        if (!refundAmount.isPositive()) {
            throw new IllegalArgumentException("Refund amount는 0보다 커야 합니다");
        }
        if (refundAmount.isGreaterThan(originalAmount)) {
            throw new IllegalArgumentException(
                    String.format("환불 금액은 원래 금액을 초과할 수 없습니다. 원래 금액: %s, 환불 금액: %s",
                            originalAmount, refundAmount)
            );
        }
    }

    private static void validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("환불 사유는 필수입니다");
        }
    }

    private static void validateTransactionId(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID는 필수입니다");
        }
    }

    private static void validateFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("실패 사유는 필수입니다");
        }
    }

    private static String generateRefundId() {
        return "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}