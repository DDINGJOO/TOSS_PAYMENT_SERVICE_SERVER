package com.teambind.payment.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teambind.payment.domain.Payment;

import java.time.LocalDateTime;

public record PaymentCancelResponse(
        // 결제 ID
        String paymentId,

        // 예약 ID
        String reservationId,

        // 결제 상태
        String status,

        // 취소 시각
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime cancelledAt
) {
    public static PaymentCancelResponse from(Payment payment) {
        return new PaymentCancelResponse(
                payment.getPaymentId(),
                payment.getReservationId(),
                payment.getStatus().name(),
                payment.getCancelledAt()
        );
    }
}