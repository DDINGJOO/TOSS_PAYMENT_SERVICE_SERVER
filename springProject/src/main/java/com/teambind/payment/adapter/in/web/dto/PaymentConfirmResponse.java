package com.teambind.payment.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teambind.payment.domain.Payment;

import java.time.LocalDateTime;

public record PaymentConfirmResponse(
        // 결제 ID
        String paymentId,

        // 예약 ID
        String reservationId,

        // 주문 ID
        String orderId,

        // 결제 키
        String paymentKey,

        // 거래 ID
        String transactionId,

        // 결제 금액
        Long amount,

        // 결제 수단
        String method,

        // 결제 상태
        String status,

        // 결제 완료 시각
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime completedAt
) {
    public static PaymentConfirmResponse from(Payment payment) {
        return new PaymentConfirmResponse(
                payment.getPaymentId(),
                payment.getReservationId(),
                payment.getOrderId(),
                payment.getPaymentKey(),
                payment.getTransactionId(),
                payment.getAmount().getValue().longValue(),
                payment.getMethod() != null ? payment.getMethod().name() : null,
                payment.getStatus().name(),
                payment.getPaidAt()
        );
    }
}