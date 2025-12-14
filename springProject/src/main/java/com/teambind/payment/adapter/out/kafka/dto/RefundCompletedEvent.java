package com.teambind.payment.adapter.out.kafka.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.teambind.payment.domain.Refund;

import java.time.LocalDateTime;

public record RefundCompletedEvent(
		// 환불 ID
		String refundId,
		
		// 결제 ID
		String paymentId,
		
		// 예약 ID
		String reservationId,
		
		// 원래 결제 금액
		Long originalAmount,
		
		// 환불 금액
		Long refundAmount,
		
		// 환불 사유
		String reason,
		
		// 환불 완료 시각
		@JsonSerialize(using = LocalDateTimeSerializer.class)
		@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime completedAt
) {
	public static RefundCompletedEvent from(Refund refund, String reservationId) {
		return new RefundCompletedEvent(
				refund.getRefundId(),
				refund.getPaymentId(),
				reservationId,
				refund.getOriginalAmount().getValue().longValue(),
				refund.getRefundAmount().getValue().longValue(),
				refund.getReason(),
				refund.getCompletedAt()
		);
	}
}
