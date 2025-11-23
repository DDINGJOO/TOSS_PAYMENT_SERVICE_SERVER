package com.teambind.payment.common.exception;

import com.teambind.common.exceptions.CustomException;
import com.teambind.common.exceptions.ErrorCode;

public class RefundException extends CustomException {

    public RefundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RefundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public RefundException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    @Override
    public String getExceptionType() {
        return "REFUND_DOMAIN";
    }

    public static RefundException notFound(String refundId) {
        return new RefundException(
                ErrorCode.REFUND_NOT_FOUND,
                "Refund not found: " + refundId
        );
    }

    public static RefundException notAllowed(String reason) {
        return new RefundException(
                ErrorCode.REFUND_NOT_ALLOWED,
                "Refund not allowed: " + reason
        );
    }

    public static RefundException amountExceeded(Long requested, Long maximum) {
        return new RefundException(
                ErrorCode.REFUND_AMOUNT_EXCEEDED,
                String.format("Refund amount exceeded - requested: %d, maximum: %d", requested, maximum)
        );
    }

    public static RefundException alreadyProcessed(String refundId) {
        return new RefundException(
                ErrorCode.REFUND_ALREADY_PROCESSED,
                "Refund already processed: " + refundId
        );
    }

    public static RefundException periodExpired(String deadline) {
        return new RefundException(
                ErrorCode.REFUND_PERIOD_EXPIRED,
                "Refund period expired. Deadline was: " + deadline
        );
    }
}
