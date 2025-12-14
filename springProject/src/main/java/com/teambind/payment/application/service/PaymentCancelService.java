package com.teambind.payment.application.service;

import com.teambind.payment.adapter.out.kafka.dto.PaymentCancelledEvent;
import com.teambind.payment.adapter.out.toss.dto.TossRefundRequest;
import com.teambind.payment.adapter.out.toss.dto.TossRefundResponse;
import com.teambind.payment.application.port.out.PaymentRepository;
import com.teambind.payment.application.port.out.TossRefundClient;
import com.teambind.payment.common.exception.PaymentException;
import com.teambind.payment.common.exception.TossApiException;
import com.teambind.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCancelService {
	
	private final PaymentRepository paymentRepository;
	private final TossRefundClient tossRefundClient;
	private final PaymentEventPublisher paymentEventPublisher;
	
	@Transactional
	public Payment cancelPayment(String paymentId, String reason) {
		log.info("결제 취소 시작 - paymentId: {}, reason: {}", paymentId, reason);
		
		// 1. Payment 조회
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> PaymentException.notFound(paymentId));
		
		try {
			// 2. 토스 결제 취소 API 호출 (전액 취소)
			TossRefundRequest request = new TossRefundRequest(
					reason,
					payment.getAmount().getValue().longValue()
			);
			
			TossRefundResponse response = tossRefundClient.cancelPayment(payment.getPaymentKey(), request);
			
			// 3. Payment 취소 처리
			payment.cancel();
			
			Payment canceledPayment = paymentRepository.save(payment);
			
			log.info("결제 취소 완료 - paymentId: {}, transactionId: {}, status: {}",
					canceledPayment.getPaymentId(), response.transactionId(), canceledPayment.getStatus());
			
			// 4. 결제 취소 이벤트 발행
			PaymentCancelledEvent event = PaymentCancelledEvent.from(canceledPayment);
			paymentEventPublisher.publishPaymentCancelledEvent(event);
			
			return canceledPayment;
			
		} catch (Exception e) {
			log.error("결제 취소 실패 - paymentId: {}, error: {}", paymentId, e.getMessage(), e);
			payment.fail("결제 취소 실패: " + e.getMessage());
			paymentRepository.save(payment);
			throw new TossApiException(
					com.teambind.common.exceptions.ErrorCode.TOSS_API_ERROR,
					"Payment cancellation failed for: " + paymentId,
					e
			);
		}
	}
}
