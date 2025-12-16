package com.teambind.payment.adapter.in.web;

import com.teambind.payment.adapter.in.web.dto.RefundRequest;
import com.teambind.payment.adapter.in.web.dto.RefundResponse;
import com.teambind.payment.application.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/refund")
@RequiredArgsConstructor
@Slf4j
public class RefundController {

	private final RefundService refundService;

	@PostMapping
	public ResponseEntity<RefundResponse> requestRefund(
			@Valid @RequestBody RefundRequest request
	) {
		log.info("환불 요청 수신 - reservationId: {}", request.reservationId());

		refundService.processRefundByReservationId(request.reservationId());

		RefundResponse response = RefundResponse.success(request.reservationId());
		log.info("환불 요청 응답 반환 - reservationId: {}", request.reservationId());

		return ResponseEntity.ok(response);
	}
}
