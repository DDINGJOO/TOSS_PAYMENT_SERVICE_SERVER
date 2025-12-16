package com.teambind.payment.adapter.out.kafka.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.teambind.payment.domain.Payment;
import com.teambind.payment.domain.Refund;

import java.time.LocalDateTime;

public record RefundCompletedEvent(
		String topic,
		String eventType,
		String refundId,
		String paymentId,
		String reservationId,
		Long originalAmount,
		Long refundAmount,
		String reason,
		@JsonSerialize(using = LocalDateTimeSerializer.class)
		@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime completedAt
) {
	private static final String TOPIC = "refund-completed";
	private static final String EVENT_TYPE = "RefundCompleted";

	public static RefundCompletedEvent from(Refund refund, String reservationId) {
		return new RefundCompletedEvent(
				TOPIC,
				EVENT_TYPE,
				refund.getRefundId(),
				refund.getPaymentId(),
				reservationId,
				refund.getOriginalAmount().getValue().longValue(),
				refund.getRefundAmount().getValue().longValue(),
				refund.getReason(),
				refund.getCompletedAt()
		);
	}

	public static RefundCompletedEvent fromPaymentCancel(Payment payment, String reason) {
		Long amount = payment.getAmount().getValue().longValue();
		return new RefundCompletedEvent(
				TOPIC,
				EVENT_TYPE,
				null,
				payment.getPaymentId(),
				payment.getReservationId(),
				amount,
				amount,
				reason,
				LocalDateTime.now()
		);
	}
}
