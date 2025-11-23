package com.teambind.payment.adapter.out.toss.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

import java.time.LocalDateTime;

public record TossRefundResponse(
        // 결제 키
        String paymentKey,

        // 주문 ID
        String orderId,

        // 거래 ID
        String transactionId,

        // 취소 금액
        Long cancelAmount,

        // 상태 (CANCELED 등)
        String status,

        // 취소 시각
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime canceledAt
) {
}