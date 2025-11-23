package com.teambind.payment.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentConfirmRequest(
        // 결제 ID
        @NotBlank(message = "결제 ID는 필수입니다")
        String paymentId,

        // 주문 ID
        @NotBlank(message = "주문 ID는 필수입니다")
        String orderId,

        // 결제 키
        @NotBlank(message = "결제 키는 필수입니다")
        String paymentKey,

        // 결제 금액
        @NotNull(message = "결제 금액은 필수입니다")
        @Positive(message = "결제 금액은 0보다 커야 합니다")
        Long amount
) {
}