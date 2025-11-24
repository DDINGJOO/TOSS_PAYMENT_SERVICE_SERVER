package com.teambind.payment.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teambind.payment.domain.Payment;

import java.time.LocalDateTime;

public record PaymentPrepareResponse(
        // 결제 ID
        String paymentId,

        // 예약 ID
        String reservationId,

        // 결제 금액
        Long amount,

        // 결제 상태
        String status,

        // 체크인 날짜
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime checkInDate,

        // 멱등성 키
        String idempotencyKey,

        // 결제 생성 시각
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt
) {
    public static PaymentPrepareResponse from(Payment payment) {
        return new PaymentPrepareResponse(
                payment.getPaymentId(),
                payment.getReservationId(),
                payment.getAmount().getValue().longValue(),
                payment.getStatus().name(),
                payment.getCheckInDate(),
                payment.getIdempotencyKey(),
                payment.getCreatedAt()
        );
    }
}