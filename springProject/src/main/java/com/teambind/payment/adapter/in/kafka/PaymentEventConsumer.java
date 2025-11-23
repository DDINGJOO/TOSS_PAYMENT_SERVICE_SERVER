package com.teambind.payment.adapter.in.kafka;

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

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PaymentPrepareService paymentPrepareService;

    @KafkaListener(
            topics = "${kafka.topics.reservation-confirmed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleReservationConfirmed(
            @Payload ReservationConfirmedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        try {
            log.info("예약 확정 이벤트 수신 - topic: {}, partition: {}, offset: {}, reservationId: {}",
                    topic, partition, offset, event.reservationId());

            // 결제 준비 처리
            paymentPrepareService.preparePayment(
                    event.reservationId(),
                    event.amount(),
                    event.checkInDate()
            );

            // 수동 커밋
            acknowledgment.acknowledge();
            log.info("예약 확정 이벤트 처리 완료 - reservationId: {}", event.reservationId());

        } catch (Exception e) {
            log.error("예약 확정 이벤트 처리 실패 - reservationId: {}, error: {}",
                    event.reservationId(), e.getMessage(), e);
            // 에러 발생 시 커밋하지 않음 (재처리됨)
            throw e;
        }
    }
}