package com.teambind.payment.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class RefundPolicy {

    private static final int FULL_REFUND_DAYS = 7;
    private static final int PARTIAL_REFUND_DAYS = 3;
    private static final BigDecimal PARTIAL_REFUND_RATE = BigDecimal.valueOf(0.5);

    private final LocalDateTime checkInDate;
    private final LocalDateTime refundRequestDate;

    private RefundPolicy(LocalDateTime checkInDate, LocalDateTime refundRequestDate) {
        this.checkInDate = checkInDate;
        this.refundRequestDate = refundRequestDate;
    }

    public static RefundPolicy of(LocalDateTime checkInDate, LocalDateTime refundRequestDate) {
        validateCheckInDate(checkInDate);
        validateRefundRequestDate(refundRequestDate);
        return new RefundPolicy(checkInDate, refundRequestDate);
    }

    public Money calculateRefundAmount(Money originalAmount) {
        long daysUntilCheckIn = ChronoUnit.DAYS.between(
                refundRequestDate.toLocalDate(),
                checkInDate.toLocalDate()
        );

        if (daysUntilCheckIn >= FULL_REFUND_DAYS) {
            // 7일 이상 남음: 100% 환불
            return originalAmount;
        } else if (daysUntilCheckIn >= PARTIAL_REFUND_DAYS) {
            // 3일 이상 7일 미만: 50% 환불
            return originalAmount.multiply(PARTIAL_REFUND_RATE);
        } else {
            // 3일 미만: 환불 불가
            throw new IllegalStateException(
                    String.format("체크인 %d일 전에는 환불이 불가능합니다. 최소 %d일 전에 환불을 요청해야 합니다.",
                            daysUntilCheckIn, PARTIAL_REFUND_DAYS)
            );
        }
    }

    public boolean isRefundable() {
        long daysUntilCheckIn = ChronoUnit.DAYS.between(
                refundRequestDate.toLocalDate(),
                checkInDate.toLocalDate()
        );
        return daysUntilCheckIn >= PARTIAL_REFUND_DAYS;
    }

    public int getRefundRate() {
        long daysUntilCheckIn = ChronoUnit.DAYS.between(
                refundRequestDate.toLocalDate(),
                checkInDate.toLocalDate()
        );

        if (daysUntilCheckIn >= FULL_REFUND_DAYS) {
            return 100;
        } else if (daysUntilCheckIn >= PARTIAL_REFUND_DAYS) {
            return 50;
        } else {
            return 0;
        }
    }

    private static void validateCheckInDate(LocalDateTime checkInDate) {
        if (checkInDate == null) {
            throw new IllegalArgumentException("Check-in date는 필수입니다");
        }
    }

    private static void validateRefundRequestDate(LocalDateTime refundRequestDate) {
        if (refundRequestDate == null) {
            throw new IllegalArgumentException("Refund request date는 필수입니다");
        }
    }
}