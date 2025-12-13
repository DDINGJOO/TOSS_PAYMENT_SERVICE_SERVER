package com.teambind.payment.adapter.out.toss.dto;

import java.time.OffsetDateTime;

public record TossPaymentConfirmResponse(
        // 결제 키
        String paymentKey,

        // 주문 ID
        String orderId,

        // 결제 타입 (NORMAL, BILLING 등)
        String type,

        // 마지막 거래 키
        String lastTransactionKey,

        // 결제 금액
        Long totalAmount,

        // 결제 수단 (카드, 가상계좌, 간편결제 등)
        String method,

        // 상태 (DONE, CANCELED 등)
        String status,

        // 결제 승인 시각 (Toss API는 ISO 8601 형식으로 타임존 포함하여 반환)
        OffsetDateTime approvedAt
) {
}