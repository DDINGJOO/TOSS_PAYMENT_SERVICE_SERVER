package com.teambind.payment.adapter.in.web;

import com.teambind.payment.adapter.in.web.dto.*;
import com.teambind.payment.application.service.PaymentCancelService;
import com.teambind.payment.application.service.PaymentConfirmService;
import com.teambind.payment.application.service.PaymentPrepareService;
import com.teambind.payment.domain.Payment;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentPrepareService paymentPrepareService;
    private final PaymentConfirmService paymentConfirmService;
    private final PaymentCancelService paymentCancelService;

    @PostMapping
    public ResponseEntity<PaymentPrepareResponse> preparePayment(
            @Valid @RequestBody PaymentPrepareRequest request
    ) {
        log.info("결제 준비 요청 수신 - reservationId: {}, amount: {}, checkInDate: {}",
                request.reservationId(), request.amount(), request.checkInDate());

        Payment payment = paymentPrepareService.preparePayment(
                request.reservationId(),
                request.amount(),
                request.checkInDate()
        );

        PaymentPrepareResponse response = PaymentPrepareResponse.from(payment);
        log.info("결제 준비 응답 반환 - paymentId: {}, status: {}",
                response.paymentId(), response.status());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentConfirmResponse> confirmPayment(
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        log.info("결제 승인 요청 수신 - paymentId: {}, orderId: {}, amount: {}",
                request.paymentId(), request.orderId(), request.amount());

        Payment payment = paymentConfirmService.confirmPayment(
                request.paymentId(),
                request.orderId(),
                request.paymentKey(),
                request.amount()
        );

        PaymentConfirmResponse response = PaymentConfirmResponse.from(payment);
        log.info("결제 승인 응답 반환 - paymentId: {}, status: {}",
                response.paymentId(), response.status());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentCancelResponse> cancelPayment(
            @PathVariable String paymentId,
            @Valid @RequestBody PaymentCancelRequest request
    ) {
        log.info("결제 취소 요청 수신 - paymentId: {}, reason: {}",
                paymentId, request.reason());

        Payment payment = paymentCancelService.cancelPayment(
                paymentId,
                request.reason()
        );

        PaymentCancelResponse response = PaymentCancelResponse.from(payment);
        log.info("결제 취소 응답 반환 - paymentId: {}, status: {}",
                response.paymentId(), response.status());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentConfirmResponse> getPayment(
            @PathVariable String paymentId
    ) {
        log.info("결제 조회 요청 수신 - paymentId: {}", paymentId);

        Payment payment = paymentPrepareService.getPayment(paymentId);

        PaymentConfirmResponse response = PaymentConfirmResponse.from(payment);
        log.info("결제 조회 응답 반환 - paymentId: {}, status: {}",
                response.paymentId(), response.status());

        return ResponseEntity.ok(response);
    }
}
