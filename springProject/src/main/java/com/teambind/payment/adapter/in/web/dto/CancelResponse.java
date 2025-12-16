package com.teambind.payment.adapter.in.web.dto;

public record CancelResponse(
		boolean success,
		String message,
		String reservationId
) {
	private static final String SUCCESS_MESSAGE = "환불 요청이 접수되었습니다";

	public static CancelResponse success(String reservationId) {
		return new CancelResponse(true, SUCCESS_MESSAGE, reservationId);
	}
}
