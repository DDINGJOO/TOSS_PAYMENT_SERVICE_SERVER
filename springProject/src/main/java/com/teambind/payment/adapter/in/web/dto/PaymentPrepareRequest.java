package com.teambind.payment.adapter.in.web.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record PaymentPrepareRequest(
        // 예약 ID
        @NotBlank(message = "예약 ID는 필수입니다")
        String reservationId,

        // 결제 금액
        @NotNull(message = "결제 금액은 필수입니다")
        @Positive(message = "결제 금액은 0보다 커야 합니다")
        Long amount,

        // 체크인 날짜 (당일 예약 가능)
        @NotNull(message = "체크인 날짜는 필수입니다")
        @FutureOrPresent(message = "체크인 날짜는 과거일 수 없습니다")
        LocalDateTime checkInDate
) {
}