package com.teambind.payment.adapter.out.toss.dto;

public record TossPaymentConfirmRequest(
        // 결제 키
        String paymentKey,

        // 주문 ID
        String orderId,

        // 결제 금액
        Long amount
) {
}