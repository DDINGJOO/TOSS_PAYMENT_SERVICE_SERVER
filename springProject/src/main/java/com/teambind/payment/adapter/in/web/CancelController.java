package com.teambind.payment.adapter.in.web;

import com.teambind.payment.adapter.in.web.dto.CancelRequest;
import com.teambind.payment.adapter.in.web.dto.CancelResponse;
import com.teambind.payment.application.service.PaymentCancelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cancel")
@RequiredArgsConstructor
@Slf4j
public class CancelController {

	private final PaymentCancelService paymentCancelService;

	@PostMapping
	public ResponseEntity<CancelResponse> cancelPayment(
			@Valid @RequestBody CancelRequest request
	) {
		log.info("결제 취소 요청 수신 - reservationId: {}", request.reservationId());

		paymentCancelService.cancelPaymentByReservationId(request.reservationId());

		CancelResponse response = CancelResponse.success(request.reservationId());
		log.info("결제 취소 응답 반환 - reservationId: {}", request.reservationId());

		return ResponseEntity.ok(response);
	}
}
