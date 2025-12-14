package com.teambind.payment.common.exception;

import com.teambind.common.exceptions.CustomException;
import com.teambind.common.exceptions.ErrorCode;

public class
PaymentException extends CustomException {
	
	public PaymentException(ErrorCode errorCode) {
		super(errorCode);
	}
	
	public PaymentException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}
	
	public PaymentException(ErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}
	
	public static PaymentException notFound(String paymentId) {
		return new PaymentException(
				ErrorCode.PAYMENT_NOT_FOUND,
				"Payment not found: " + paymentId
		);
	}
	
	public static PaymentException notFoundByOrderId(String orderId) {
		return new PaymentException(
				ErrorCode.PAYMENT_NOT_FOUND,
				"Payment not found by orderId: " + orderId
		);
	}
	
	public static PaymentException alreadyCompleted(String paymentId) {
		return new PaymentException(
				ErrorCode.PAYMENT_ALREADY_COMPLETED,
				"Payment already completed: " + paymentId
		);
	}
	
	public static PaymentException alreadyCancelled(String paymentId) {
		return new PaymentException(
				ErrorCode.PAYMENT_ALREADY_CANCELLED,
				"Payment already cancelled: " + paymentId
		);
	}
	
	public static PaymentException amountMismatch(Long expected, Long actual) {
		return new PaymentException(
				ErrorCode.PAYMENT_AMOUNT_MISMATCH,
				String.format("Payment amount mismatch - expected: %d, actual: %d", expected, actual)
		);
	}
	
	public static PaymentException notCompleted(String paymentId) {
		return new PaymentException(
				ErrorCode.PAYMENT_NOT_COMPLETED,
				"Payment not completed: " + paymentId
		);
	}
	
	public static PaymentException invalidStatus(String currentStatus, String expectedStatus) {
		return new PaymentException(
				ErrorCode.INVALID_PAYMENT_STATUS,
				String.format("Invalid payment status - current: %s, expected: %s", currentStatus, expectedStatus)
		);
	}
	
	@Override
	public String getExceptionType() {
		return "PAYMENT_DOMAIN";
	}
}
