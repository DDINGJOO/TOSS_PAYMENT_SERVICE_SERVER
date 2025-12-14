package com.teambind.payment.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentConfirmRequest(
		// 주문 ID (= reservationId, 결제 조회 키)
		@NotBlank(message = "주문 ID는 필수입니다")
		String orderId,
		
		// 결제 키 (토스에서 발급)
		@NotBlank(message = "결제 키는 필수입니다")
		String paymentKey,
		
		// 결제 금액
		@NotNull(message = "결제 금액은 필수입니다")
		@Positive(message = "결제 금액은 0보다 커야 합니다")
		Long amount
) {
}
