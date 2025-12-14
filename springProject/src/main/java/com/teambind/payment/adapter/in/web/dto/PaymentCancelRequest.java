package com.teambind.payment.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentCancelRequest(
		// 취소 사유
		@NotBlank(message = "취소 사유는 필수입니다")
		String reason
) {
}
