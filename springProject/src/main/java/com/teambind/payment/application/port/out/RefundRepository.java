package com.teambind.payment.application.port.out;

import com.teambind.payment.domain.Refund;

import java.util.List;
import java.util.Optional;

public interface RefundRepository {
	
	// 환불 저장
	Refund save(Refund refund);
	
	// 환불 ID로 조회
	Optional<Refund> findById(String refundId);
	
	// 결제 ID로 환불 목록 조회 (한 결제에 여러 환불이 있을 수 있음)
	List<Refund> findByPaymentId(String paymentId);
}
