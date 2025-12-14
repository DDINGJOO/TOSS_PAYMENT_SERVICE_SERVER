package com.teambind.payment.adapter.in.kafka.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

import java.time.LocalDateTime;

/**
 * 예약 서버에서 발행하는 예약 완료 이벤트
 * Topic: complete-reservation-info
 */
public record ReservationConfirmedEvent(
		// 토픽명
		String topic,
		
		// 이벤트 타입
		String eventType,
		
		// 예약 ID
		String reservationId,
		
		// 결제 금액
		Long totalPrice,
		
		// 체크인 날짜
		@JsonDeserialize(using = LocalDateTimeDeserializer.class)
		LocalDateTime checkInDate,
		
		// 이벤트 발생 시각
		@JsonDeserialize(using = LocalDateTimeDeserializer.class)
		LocalDateTime occurredAt
) {
}





