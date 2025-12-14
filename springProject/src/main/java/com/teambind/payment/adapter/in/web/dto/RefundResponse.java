package com.teambind.payment.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teambind.payment.domain.Refund;

import java.time.LocalDateTime;

public record RefundResponse(
		// 환불 ID
		String refundId,
		
		// 결제 ID
		String paymentId,
		
		// 원래 결제 금액
		Long originalAmount,
		
		// 환불 금액
		Long refundAmount,
		
		// 환불 상태
		String status,
		
		// 환불 사유
		String reason,
		
		// 거래 ID
		String transactionId,
		
		// 환불 요청 시각
		@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime requestedAt,
		
		// 환불 완료 시각
		@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime completedAt
) {
	public static RefundResponse from(Refund refund) {
		return new RefundResponse(
				refund.getRefundId(),
				refund.getPaymentId(),
				refund.getOriginalAmount().getValue().longValue(),
				refund.getRefundAmount().getValue().longValue(),
				refund.getStatus().name(),
				refund.getReason(),
				refund.getTransactionId(),
				refund.getRequestedAt(),
				refund.getCompletedAt()
		);
	}
}
