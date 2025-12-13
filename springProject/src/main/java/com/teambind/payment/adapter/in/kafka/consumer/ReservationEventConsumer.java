package com.teambind.payment.adapter.in.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teambind.payment.adapter.in.kafka.dto.ReservationConfirmedEvent;
import com.teambind.payment.application.service.PaymentPrepareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Reservation Service로부터 예약 확정 이벤트를 수신하는 Consumer
 * - Dual Path Architecture의 Kafka 이벤트 경로
 * - 예약 확정 시 자동으로 결제를 PREPARED 상태로 생성
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationEventConsumer {

    private final PaymentPrepareService paymentPrepareService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${kafka.topics.reservation-confirmed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeReservationConfirmed(
            @Payload Map<String, Object> message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) Long offset,
            Acknowledgment acknowledgment
    ) {
        try {
            log.info("Received ReservationConfirmedEvent - topic: {}, offset: {}, key: {}", topic, offset, key);

            ReservationConfirmedEvent event = objectMapper.convertValue(message, ReservationConfirmedEvent.class);

            log.info("Processing reservation confirmed - reservationId: {}, totalPrice: {}, checkInDate: {}",
                    event.reservationId(), event.totalPrice(), event.checkInDate());

            // Dual Path Architecture - Kafka 이벤트 경로로 결제 준비
            paymentPrepareService.preparePayment(
                    event.reservationId(),
                    event.totalPrice(),
                    event.checkInDate()
            );

            log.info("Payment prepared successfully from reservation event - reservationId: {}", event.reservationId());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process ReservationConfirmedEvent - topic: {}, offset: {}, error: {}",
                    topic, offset, e.getMessage(), e);
            // Do not ACK on failure - will be reprocessed
        }
    }
}