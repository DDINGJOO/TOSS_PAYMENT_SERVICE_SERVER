package com.teambind.payment.adapter.out.toss.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

import java.time.LocalDateTime;

public record TossPaymentConfirmResponse(
        // 결제 키
        String paymentKey,

        // 주문 ID
        String orderId,

        // 결제 타입 (NORMAL, BILLING 등)
        String type,

        // 거래 ID
        String transactionId,

        // 결제 금액
        Long totalAmount,

        // 결제 수단 (카드, 가상계좌, 간편결제 등)
        String method,

        // 상태 (DONE, CANCELED 등)
        String status,

        // 결제 승인 시각
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime approvedAt
) {
}