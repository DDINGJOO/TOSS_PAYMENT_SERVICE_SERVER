package com.teambind.payment.adapter.in.web;

import com.teambind.payment.adapter.in.web.dto.RefundRequest;
import com.teambind.payment.adapter.in.web.dto.RefundResponse;
import com.teambind.payment.application.service.RefundService;
import com.teambind.payment.domain.Refund;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
@Slf4j
public class RefundController {

    private final RefundService refundService;

    @PostMapping
    public ResponseEntity<RefundResponse> requestRefund(
            @Valid @RequestBody RefundRequest request
    ) {
        log.info("환불 요청 수신 - paymentId: {}, reason: {}",
                request.paymentId(), request.reason());

        try {
            Refund refund = refundService.processRefund(
                    request.paymentId(),
                    request.reason()
            );

            RefundResponse response = RefundResponse.from(refund);
            log.info("환불 요청 응답 반환 - refundId: {}, status: {}, refundAmount: {}",
                    response.refundId(), response.status(), response.refundAmount());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("환불 요청 실패 (잘못된 요청) - paymentId: {}, error: {}",
                    request.paymentId(), e.getMessage());
            throw e;

        } catch (IllegalStateException e) {
            log.error("환불 요청 실패 (상태 오류) - paymentId: {}, error: {}",
                    request.paymentId(), e.getMessage());
            throw e;

        } catch (Exception e) {
            log.error("환불 요청 실패 (서버 오류) - paymentId: {}, error: {}",
                    request.paymentId(), e.getMessage(), e);
            throw new RuntimeException("환불 처리 중 오류가 발생했습니다", e);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException e) {
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