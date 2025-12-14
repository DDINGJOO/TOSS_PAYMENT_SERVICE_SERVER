package com.teambind.payment.application.port.out;

import com.teambind.payment.adapter.out.toss.dto.TossPaymentConfirmRequest;
import com.teambind.payment.adapter.out.toss.dto.TossPaymentConfirmResponse;

public interface TossPaymentClient {
	
	// 토스 결제 승인 요청
	TossPaymentConfirmResponse confirmPayment(TossPaymentConfirmRequest request);
}
