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

	private static final String DEFAULT_REFUND_REASON = "사용자 취소 요청";

	private final PaymentRepository paymentRepository;
	private final RefundRepository refundRepository;
	private final TossRefundClient tossRefundClient;
	private final PaymentEventPublisher paymentEventPublisher;

	@Transactional
	public Refund processRefundByReservationId(String reservationId) {
		log.info("환불 처리 시작 - reservationId: {}", reservationId);

		Payment payment = paymentRepository.findByReservationId(reservationId)
				.orElseThrow(() -> PaymentException.notFoundByReservationId(reservationId));

		return processRefundInternal(payment, DEFAULT_REFUND_REASON);
	}

	@Transactional
	public Refund processRefund(String paymentId, String reason) {
		log.info("환불 처리 시작 - paymentId: {}, reason: {}", paymentId, reason);

		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> PaymentException.notFound(paymentId));

		return processRefundInternal(payment, reason);
	}

	private Refund processRefundInternal(Payment payment, String reason) {
		String paymentId = payment.getPaymentId();
		payment.validateRefundable();

		RefundPolicy policy = RefundPolicy.of(payment.getCheckInDate(), LocalDateTime.now());
		Money refundAmount = policy.calculateRefundAmount(payment.getAmount());

		log.info("환불 금액 계산 완료 - paymentId: {}, originalAmount: {}, refundAmount: {}, refundRate: {}%",
				paymentId, payment.getAmount(), refundAmount, policy.getRefundRate());

		Refund refund = Refund.request(paymentId, payment.getAmount(), refundAmount, reason);
		refund.approve();
		refundRepository.save(refund);

		try {
			TossRefundRequest tossRequest = new TossRefundRequest(
					reason,
					refundAmount.getValue().longValue()
			);

			TossRefundResponse tossResponse = tossRefundClient.cancelPayment(payment.getPaymentKey(), tossRequest);

			refund.complete(tossResponse.transactionId());
			payment.cancel();

			paymentRepository.save(payment);
			Refund completedRefund = refundRepository.save(refund);

			log.info("환불 처리 완료 - refundId: {}, transactionId: {}, refundAmount: {}",
					completedRefund.getRefundId(), completedRefund.getTransactionId(), refundAmount);

			RefundCompletedEvent refundEvent = RefundCompletedEvent.from(completedRefund, payment.getReservationId());
			paymentEventPublisher.publishRefundCompletedEvent(refundEvent);

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
