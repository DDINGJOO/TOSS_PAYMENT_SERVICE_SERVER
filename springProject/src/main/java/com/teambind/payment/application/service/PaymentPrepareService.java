package com.teambind.payment.application.service;

import com.teambind.payment.application.port.out.PaymentRepository;
import com.teambind.payment.common.exception.PaymentException;
import com.teambind.payment.domain.Money;
import com.teambind.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentPrepareService {
	
	private final PaymentRepository paymentRepository;
	
	@Transactional
	public Payment preparePayment(String reservationId, Long amount, LocalDateTime checkInDate) {
		log.info("결제 준비 시작 - reservationId: {}, amount: {}", reservationId, amount);
		
		// 멱등성 체크 - 이미 처리된 예약인지 확인
		return paymentRepository.findByReservationId(reservationId)
				.map(existingPayment -> {
					log.info("이미 처리된 예약입니다 - paymentId: {}", existingPayment.getPaymentId());
					return existingPayment;
				})
				.orElseGet(() -> {
					try {
						// 새로운 결제 준비
						Payment payment = Payment.prepare(
								reservationId,
								Money.of(amount),
								checkInDate
						);
						
						Payment savedPayment = paymentRepository.save(payment);
						log.info("결제 준비 완료 - paymentId: {}, status: {}",
								savedPayment.getPaymentId(), savedPayment.getStatus());
						
						return savedPayment;
					} catch (DataIntegrityViolationException e) {
						// Race Condition 발생 (API와 이벤트 동시 처리)
						// DB unique 제약 위반 → 이미 저장된 레코드 조회
						log.warn("동시 요청 감지 - reservationId: {}. 기존 결제 정보 조회 중...", reservationId);
						return paymentRepository.findByReservationId(reservationId)
								.orElseThrow(() -> new IllegalStateException(
										"결제 저장 실패 및 재조회 실패 - reservationId: " + reservationId));
					}
				});
	}
	
	@Transactional(readOnly = true)
	public Payment getPayment(String paymentId) {
		log.info("결제 조회 - paymentId: {}", paymentId);
		
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> PaymentException.notFound(paymentId));
	}
}
