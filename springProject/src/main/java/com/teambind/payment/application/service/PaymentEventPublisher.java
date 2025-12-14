package com.teambind.payment.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teambind.payment.adapter.out.kafka.dto.PaymentCancelledEvent;
import com.teambind.payment.adapter.out.kafka.dto.PaymentCompletedEvent;
import com.teambind.payment.adapter.out.kafka.dto.RefundCompletedEvent;
import com.teambind.payment.application.port.out.PaymentEventRepository;
import com.teambind.payment.domain.EventType;
import com.teambind.payment.domain.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {
	
	private final PaymentEventRepository paymentEventRepository;
	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final ObjectMapper objectMapper;
	
	@Value("${kafka.topics.payment-completed}")
	private String paymentCompletedTopic;
	
	@Value("${kafka.topics.payment-cancelled}")
	private String paymentCancelledTopic;
	
	@Value("${kafka.topics.refund-completed}")
	private String refundCompletedTopic;
	
	@Transactional(propagation = Propagation.MANDATORY)
	public void publishPaymentCompletedEvent(PaymentCompletedEvent event) {
		log.info("결제 완료 이벤트 저장 - paymentId: {}", event.paymentId());
		
		try {
			String payload = objectMapper.writeValueAsString(event);
			
			PaymentEvent paymentEvent = PaymentEvent.create(
					event.paymentId(),
					EventType.PAYMENT_COMPLETED,
					payload
			);
			
			paymentEventRepository.save(paymentEvent);
			log.info("결제 완료 이벤트 저장 완료 - eventId: {}", paymentEvent.getEventId());
			
		} catch (JsonProcessingException e) {
			log.error("이벤트 직렬화 실패 - paymentId: {}", event.paymentId(), e);
			throw new RuntimeException("이벤트 직렬화 실패", e);
		}
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public void publishPaymentCancelledEvent(PaymentCancelledEvent event) {
		log.info("결제 취소 이벤트 저장 - paymentId: {}", event.paymentId());
		
		try {
			String payload = objectMapper.writeValueAsString(event);
			
			PaymentEvent paymentEvent = PaymentEvent.create(
					event.paymentId(),
					EventType.PAYMENT_CANCELLED,
					payload
			);
			
			paymentEventRepository.save(paymentEvent);
			log.info("결제 취소 이벤트 저장 완료 - eventId: {}", paymentEvent.getEventId());
			
		} catch (JsonProcessingException e) {
			log.error("이벤트 직렬화 실패 - paymentId: {}", event.paymentId(), e);
			throw new RuntimeException("이벤트 직렬화 실패", e);
		}
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public void publishRefundCompletedEvent(RefundCompletedEvent event) {
		log.info("환불 완료 이벤트 저장 - refundId: {}, paymentId: {}", event.refundId(), event.paymentId());
		
		try {
			String payload = objectMapper.writeValueAsString(event);
			
			PaymentEvent paymentEvent = PaymentEvent.create(
					event.paymentId(),
					EventType.REFUND_COMPLETED,
					payload
			);
			
			paymentEventRepository.save(paymentEvent);
			log.info("환불 완료 이벤트 저장 완료 - eventId: {}", paymentEvent.getEventId());
			
		} catch (JsonProcessingException e) {
			log.error("이벤트 직렬화 실패 - refundId: {}", event.refundId(), e);
			throw new RuntimeException("이벤트 직렬화 실패", e);
		}
	}
	
	public void sendEventToKafka(PaymentEvent event) {
		try {
			String topic = getTopicByEventType(event.getEventType());
			Object eventPayload = objectMapper.readValue(event.getPayload(), Object.class);
			
			kafkaTemplate.send(topic, event.getAggregateId(), eventPayload)
					.whenComplete((result, ex) -> {
						if (ex != null) {
							log.error("Kafka 발행 실패 - eventId: {}, type: {}, error: {}",
									event.getEventId(), event.getEventType(), ex.getMessage(), ex);
							event.markAsFailed(ex.getMessage());
							paymentEventRepository.save(event);
						} else {
							log.info("Kafka 발행 성공 - eventId: {}, type: {}, topic: {}",
									event.getEventId(), event.getEventType(), topic);
							event.markAsPublished();
							paymentEventRepository.save(event);
						}
					});
			
		} catch (Exception e) {
			log.error("Kafka 발행 중 오류 - eventId: {}", event.getEventId(), e);
			event.markAsFailed(e.getMessage());
			paymentEventRepository.save(event);
		}
	}
	
	private String getTopicByEventType(EventType eventType) {
		return switch (eventType) {
			case PAYMENT_COMPLETED -> paymentCompletedTopic;
			case PAYMENT_CANCELLED -> paymentCancelledTopic;
			case REFUND_COMPLETED -> refundCompletedTopic;
		};
	}
}
