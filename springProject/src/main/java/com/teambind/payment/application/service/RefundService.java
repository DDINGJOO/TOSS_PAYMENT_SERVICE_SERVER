package com.teambind.payment.application.service;

import com.teambind.payment.adapter.out.kafka.dto.PaymentCancelledEvent;
import com.teambind.payment.adapter.out.kafka.dto.RefundCompletedEvent;
import com.teambind.payment.adapter.out.toss.dto.TossRefundRequest;
import com.teambind.payment.adapter.out.toss.dto.TossRefundResponse;
import com.teambind.payment.application.port.out.PaymentRepository;
import com.teambind.payment.application.port.out.RefundRepository;
import com.teambind.payment.application.port.out.TossRefundClient;
import com.teambind.payment.common.exception.PaymentException;
import com.teambind.payment.common.exception.RefundException;
import com.teambind.payment.domain.Money;
import com.teambind.payment.domain.Payment;
import com.teambind.payment.domain.Refund;
import com.teambind.payment.domain.RefundPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {
	
	private final PaymentRepository paymentRepository;
	private final RefundRepository refundRepository;
	private final TossRefundClient tossRefundClient;
	private final PaymentEventPublisher paymentEventPublisher;
	
	@Transactional
	public Refund processRefund(String paymentId, String reason) {
		log.info("환불 처리 시작 - paymentId: {}, reason: {}", paymentId, reason);
		
		// 1. Payment 조회 및 환불 가능 여부 확인
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> PaymentException.notFound(paymentId));
		
		payment.validateRefundable();
		
		// 2. 환불 정책에 따라 환불 금액 계산
		RefundPolicy policy = RefundPolicy.of(payment.getCheckInDate(), LocalDateTime.now());
		Money refundAmount = policy.calculateRefundAmount(payment.getAmount());
		
		log.info("환불 금액 계산 완료 - paymentId: {}, originalAmount: {}, refundAmount: {}, refundRate: {}%",
				paymentId, payment.getAmount(), refundAmount, policy.getRefundRate());
		
		// 3. Refund 엔티티 생성 및 저장
		Refund refund = Refund.request(paymentId, payment.getAmount(), refundAmount, reason);
		refund.approve();
		Refund savedRefund = refundRepository.save(refund);
		
		try {
			// 4. 토스 환불 API 호출
			TossRefundRequest request = new TossRefundRequest(
					reason,
					refundAmount.getValue().longValue()
			);
			
			TossRefundResponse response = tossRefundClient.cancelPayment(payment.getPaymentKey(), request);
			
			// 5. 환불 완료 처리
			refund.complete(response.transactionId());
			
			// 6. Payment 취소 처리
			payment.cancel();
			
			paymentRepository.save(payment);
			Refund completedRefund = refundRepository.save(refund);
			
			log.info("환불 처리 완료 - refundId: {}, transactionId: {}, refundAmount: {}",
					completedRefund.getRefundId(), completedRefund.getTransactionId(), refundAmount);
			
			// 7. 환불 완료 이벤트 발행 (재고 롤백을 위해 reservationId 포함)
			RefundCompletedEvent refundEvent = RefundCompletedEvent.from(completedRefund, payment.getReservationId());
			paymentEventPublisher.publishRefundCompletedEvent(refundEvent);
			
			// 8. 결제 취소 이벤트 발행
			PaymentCancelledEvent cancelledEvent = PaymentCancelledEvent.from(payment);
			paymentEventPublisher.publishPaymentCancelledEvent(cancelledEvent);
			
			return completedRefund;
			
		} catch (Exception e) {
			log.error("환불 처리 실패 - paymentId: {}, error: {}", paymentId, e.getMessage(), e);
			refund.fail(e.getMessage());
			refundRepository.save(refund);
			throw new RefundException(
					com.teambind.common.exceptions.ErrorCode.REFUND_PROCESSING_FAILED,
					"Refund processing failed for payment: " + paymentId,
					e
			);
		}
	}
	
	@Transactional(readOnly = true)
	public Refund getRefund(String refundId) {
		log.info("환불 조회 - refundId: {}", refundId);
		
		return refundRepository.findById(refundId)
				.orElseThrow(() -> RefundException.notFound(refundId));
	}
}
