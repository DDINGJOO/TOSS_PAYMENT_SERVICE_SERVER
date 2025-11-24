package com.teambind.payment.adapter.in.kafka.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

import java.time.LocalDateTime;

public record ReservationConfirmedEvent(
        // 예약 ID
        String reservationId,

        // 결제 금액
        Long amount,

        // 체크인 날짜
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        LocalDateTime checkInDate
) {
}




