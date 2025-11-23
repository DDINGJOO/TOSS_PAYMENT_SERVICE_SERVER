package com.teambind.payment.application.service;

import com.teambind.payment.adapter.out.kafka.dto.PaymentCompletedEvent;
import com.teambind.payment.adapter.out.toss.dto.TossPaymentConfirmRequest;
import com.teambind.payment.adapter.out.toss.dto.TossPaymentConfirmResponse;
import com.teambind.payment.application.port.out.PaymentRepository;
import com.teambind.payment.application.port.out.TossPaymentClient;
import com.teambind.payment.common.exception.PaymentException;
import com.teambind.payment.common.exception.TossApiException;
import com.teambind.payment.domain.Money;
import com.teambind.payment.domain.Payment;
import com.teambind.payment.domain.PaymentMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentConfirmService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentEventPublisher paymentEventPublisher;

    @Transactional
    public Payment confirmPayment(String paymentId, String orderId, String paymentKey, Long amount) {
        log.info("결제 승인 시작 - paymentId: {}, orderId: {}, amount: {}", paymentId, orderId, amount);

        // 1. Payment 조회
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> PaymentException.notFound(paymentId));

        // 2. 금액 검증
        payment.validateAmount(Money.of(amount));

        // 3. 토스 결제 승인 요청
        TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(
                paymentKey,
                orderId,
                amount
        );

        TossPaymentConfirmResponse response = tossPaymentClient.confirmPayment(request);

        // 4. 결제 완료 처리
        PaymentMethod method = PaymentMethod.valueOf(mapTossMethodToPaymentMethod(response.method()));
        payment.complete(orderId, paymentKey, response.transactionId(), method);

        // 5. 저장
        Payment savedPayment = paymentRepository.save(payment);
        log.info("결제 승인 완료 - paymentId: {}, status: {}, method: {}",
                savedPayment.getPaymentId(), savedPayment.getStatus(), savedPayment.getMethod());

        // 6. 결제 완료 이벤트 발행
        PaymentCompletedEvent event = PaymentCompletedEvent.from(savedPayment);
        paymentEventPublisher.publishPaymentCompletedEvent(event);

        return savedPayment;
    }

    private String mapTossMethodToPaymentMethod(String tossMethod) {
        // 토스 결제 수단을 도메인 PaymentMethod로 매핑
        return switch (tossMethod.toUpperCase()) {
            case "CARD", "카드" -> "CARD";
            case "VIRTUAL_ACCOUNT", "가상계좌" -> "VIRTUAL_ACCOUNT";
            case "EASY_PAY", "간편결제", "TOSSPAY" -> "EASY_PAY";
            default -> "CARD"; // 기본값
        };
    }
}