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
		
		Refund refund = refundService.processRefund(
				request.paymentId(),
				request.reason()
		);
		
		RefundResponse response = RefundResponse.from(refund);
		log.info("환불 요청 응답 반환 - refundId: {}, status: {}, refundAmount: {}",
				response.refundId(), response.status(), response.refundAmount());
		
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(response);
	}
	
	@GetMapping("/{refundId}")
	public ResponseEntity<RefundResponse> getRefund(
			@PathVariable String refundId
	) {
		log.info("환불 조회 요청 수신 - refundId: {}", refundId);
		
		Refund refund = refundService.getRefund(refundId);
		
		RefundResponse response = RefundResponse.from(refund);
		log.info("환불 조회 응답 반환 - refundId: {}, status: {}, refundAmount: {}",
				response.refundId(), response.status(), response.refundAmount());
		
		return ResponseEntity.ok(response);
	}
}
