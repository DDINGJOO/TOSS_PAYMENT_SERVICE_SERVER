package com.teambind.payment.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelRequest(
		@NotBlank(message = "예약 ID는 필수입니다")
		String reservationId
) {
}
