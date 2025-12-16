package com.teambind.payment.application.service;

import com.teambind.payment.adapter.out.kafka.dto.RefundCompletedEvent;
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

	private static final String DEFAULT_CANCEL_REASON = "사용자 취소 요청";

	private final PaymentRepository paymentRepository;
	private final TossRefundClient tossRefundClient;
	private final PaymentEventPublisher paymentEventPublisher;

	@Transactional
	public Payment cancelPaymentByReservationId(String reservationId) {
		log.info("결제 취소 시작 - reservationId: {}", reservationId);

		Payment payment = paymentRepository.findByReservationId(reservationId)
				.orElseThrow(() -> PaymentException.notFoundByReservationId(reservationId));

		return cancelPaymentInternal(payment, DEFAULT_CANCEL_REASON);
	}

	@Transactional
	public Payment cancelPayment(String paymentId, String reason) {
		log.info("결제 취소 시작 - paymentId: {}, reason: {}", paymentId, reason);

		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> PaymentException.notFound(paymentId));

		return cancelPaymentInternal(payment, reason);
	}

	private Payment cancelPaymentInternal(Payment payment, String reason) {
		String paymentId = payment.getPaymentId();

		try {
			TossRefundRequest tossRequest = new TossRefundRequest(
					reason,
					payment.getAmount().getValue().longValue()
			);

			TossRefundResponse tossResponse = tossRefundClient.cancelPayment(payment.getPaymentKey(), tossRequest);

			payment.cancel();
			Payment canceledPayment = paymentRepository.save(payment);

			log.info("결제 취소 완료 - paymentId: {}, transactionId: {}, status: {}",
					canceledPayment.getPaymentId(), tossResponse.transactionId(), canceledPayment.getStatus());

			RefundCompletedEvent event = RefundCompletedEvent.fromPaymentCancel(canceledPayment, reason);
			paymentEventPublisher.publishRefundCompletedEvent(event);

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
