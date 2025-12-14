package com.teambind.payment.domain;

public enum EventType {
	PAYMENT_COMPLETED,      // 결제 완료 이벤트
	PAYMENT_CANCELLED,      // 결제 취소 이벤트
	REFUND_COMPLETED        // 환불 완료 이벤트
}
