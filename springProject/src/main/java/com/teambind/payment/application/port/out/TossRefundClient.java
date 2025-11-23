package com.teambind.payment.application.port.out;

import com.teambind.payment.adapter.out.toss.dto.TossRefundRequest;
import com.teambind.payment.adapter.out.toss.dto.TossRefundResponse;

public interface TossRefundClient {

    // 토스 결제 취소(환불) 요청
    TossRefundResponse cancelPayment(String paymentKey, TossRefundRequest request);
}