package com.teambind.payment.common.exception;

import com.teambind.common.exceptions.CustomException;
import com.teambind.common.exceptions.ErrorCode;

public class TossApiException extends CustomException {
	
	public TossApiException(ErrorCode errorCode) {
		super(errorCode);
	}
	
	public TossApiException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}
	
	public TossApiException(ErrorCode errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}
	
	public static TossApiException apiError(String message) {
		return new TossApiException(
				ErrorCode.TOSS_API_ERROR,
				"Toss API error: " + message
		);
	}
	
	public static TossApiException timeout() {
		return new TossApiException(
				ErrorCode.TOSS_API_TIMEOUT,
				"Toss API request timeout"
		);
	}
	
	public static TossApiException invalidResponse(String message) {
		return new TossApiException(
				ErrorCode.TOSS_INVALID_RESPONSE,
				"Invalid Toss API response: " + message
		);
	}
	
	@Override
	public String getExceptionType() {
		return "EXTERNAL_API";
	}
}
