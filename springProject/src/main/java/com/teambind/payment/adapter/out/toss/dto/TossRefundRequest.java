package com.teambind.payment.adapter.out.toss.dto;

public record TossRefundRequest(
        // 취소 사유
        String cancelReason,

        // 취소 금액 (부분 취소 시 사용)
        Long cancelAmount
) {
}