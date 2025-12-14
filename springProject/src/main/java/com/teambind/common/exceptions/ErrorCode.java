package com.teambind.common.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
	// Place 관련 에러 (PLACE_0XX)
	
	// 권한 관련 에러 (AUTH_0XX)
	UNAUTHORIZED("AUTH_001", "Unauthorized access", HttpStatus.UNAUTHORIZED),
	FORBIDDEN("AUTH_002", "Access forbidden", HttpStatus.FORBIDDEN),
	INSUFFICIENT_PERMISSION("AUTH_003", "Insufficient permission", HttpStatus.FORBIDDEN),
	
	// 검증 관련 에러 (VALIDATION_0XX)
	INVALID_INPUT("VALIDATION_001", "Invalid input", HttpStatus.BAD_REQUEST),
	REQUIRED_FIELD_MISSING("VALIDATION_002", "Required field is missing", HttpStatus.BAD_REQUEST),
	INVALID_FORMAT("VALIDATION_003", "Invalid format", HttpStatus.BAD_REQUEST),
	VALUE_OUT_OF_RANGE("VALIDATION_004", "Value is out of range", HttpStatus.BAD_REQUEST),
	
	// 결제 관련 에러 (PAYMENT_0XX)
	PAYMENT_NOT_FOUND("PAYMENT_001", "Payment not found", HttpStatus.NOT_FOUND),
	PAYMENT_ALREADY_COMPLETED("PAYMENT_002", "Payment already completed", HttpStatus.CONFLICT),
	PAYMENT_ALREADY_CANCELLED("PAYMENT_003", "Payment already cancelled", HttpStatus.CONFLICT),
	PAYMENT_AMOUNT_MISMATCH("PAYMENT_004", "Payment amount mismatch", HttpStatus.BAD_REQUEST),
	PAYMENT_NOT_COMPLETED("PAYMENT_005", "Payment not completed", HttpStatus.BAD_REQUEST),
	INVALID_PAYMENT_STATUS("PAYMENT_006", "Invalid payment status", HttpStatus.BAD_REQUEST),
	PAYMENT_CONFIRMATION_FAILED("PAYMENT_007", "Payment confirmation failed", HttpStatus.BAD_GATEWAY),
	
	// 환불 관련 에러 (REFUND_0XX)
	REFUND_NOT_FOUND("REFUND_001", "Refund not found", HttpStatus.NOT_FOUND),
	REFUND_NOT_ALLOWED("REFUND_002", "Refund not allowed", HttpStatus.BAD_REQUEST),
	REFUND_AMOUNT_EXCEEDED("REFUND_003", "Refund amount exceeded", HttpStatus.BAD_REQUEST),
	REFUND_ALREADY_PROCESSED("REFUND_004", "Refund already processed", HttpStatus.CONFLICT),
	REFUND_PERIOD_EXPIRED("REFUND_005", "Refund period expired", HttpStatus.BAD_REQUEST),
	REFUND_PROCESSING_FAILED("REFUND_006", "Refund processing failed", HttpStatus.BAD_GATEWAY),
	
	// 외부 API 에러 (TOSS_0XX)
	TOSS_API_ERROR("TOSS_001", "Toss API error", HttpStatus.BAD_GATEWAY),
	TOSS_API_TIMEOUT("TOSS_002", "Toss API timeout", HttpStatus.GATEWAY_TIMEOUT),
	TOSS_INVALID_RESPONSE("TOSS_003", "Invalid Toss API response", HttpStatus.BAD_GATEWAY),
	
	// 시스템 에러 (SYSTEM_0XX)
	INTERNAL_SERVER_ERROR("SYSTEM_001", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
	DATABASE_ERROR("SYSTEM_002", "Database error", HttpStatus.INTERNAL_SERVER_ERROR),
	EXTERNAL_API_ERROR("SYSTEM_003", "External API error", HttpStatus.BAD_GATEWAY),
	CACHE_ERROR("SYSTEM_004", "Cache error", HttpStatus.INTERNAL_SERVER_ERROR),
	EVENT_PUBLISH_FAILED("SYSTEM_005", "Failed to publish event", HttpStatus.INTERNAL_SERVER_ERROR),
	;
	private final String errCode;
	private final String message;
	private final HttpStatus status;
	
	ErrorCode(String errCode, String message, HttpStatus status) {
		
		this.status = status;
		this.errCode = errCode;
		this.message = message;
	}
	
	@Override
	public String toString() {
		return "ErrorCode{"
				+ " status='"
				+ status
				+ '\''
				+ "errCode='"
				+ errCode
				+ '\''
				+ ", message='"
				+ message
				+ '\''
				+ '}';
	}
}
