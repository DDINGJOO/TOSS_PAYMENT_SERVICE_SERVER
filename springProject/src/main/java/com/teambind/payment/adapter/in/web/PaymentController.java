package com.teambind.payment.adapter.in.web;

import com.teambind.payment.adapter.in.web.dto.PaymentConfirmRequest;
import com.teambind.payment.adapter.in.web.dto.PaymentConfirmResponse;
import com.teambind.payment.application.service.PaymentConfirmService;
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

    private final PaymentConfirmService paymentConfirmService;

    @PostMapping("/confirm")
    public ResponseEntity<PaymentConfirmResponse> confirmPayment(
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        log.info("결제 승인 요청 수신 - paymentId: {}, orderId: {}, amount: {}",
                request.paymentId(), request.orderId(), request.amount());

        try {
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

        } catch (IllegalArgumentException e) {
            log.error("결제 승인 실패 (잘못된 요청) - paymentId: {}, error: {}",
                    request.paymentId(), e.getMessage());
            throw e;

        } catch (Exception e) {
            log.error("결제 승인 실패 (서버 오류) - paymentId: {}, error: {}",
                    request.paymentId(), e.getMessage(), e);
            throw new RuntimeException("결제 승인 처리 중 오류가 발생했습니다", e);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", e.getMessage()));
    }

    private record ErrorResponse(String code, String message) {
    }
}
