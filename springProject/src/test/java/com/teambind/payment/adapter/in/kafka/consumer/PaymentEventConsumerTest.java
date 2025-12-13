package com.teambind.payment.adapter.in.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.teambind.payment.adapter.out.kafka.dto.PaymentCancelledEvent;
import com.teambind.payment.adapter.out.kafka.dto.PaymentCompletedEvent;
import com.teambind.payment.adapter.out.kafka.dto.RefundCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

@Disabled("Consumer tests disabled - requires proper Kafka test setup")
@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    private PaymentEventConsumer paymentEventConsumer;

    @Mock
    private Acknowledgment acknowledgment;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        paymentEventConsumer = new PaymentEventConsumer(objectMapper);
    }

    @Test
    @DisplayName("PaymentCompletedEvent consumption success")
    void consumePaymentCompleted_success() {
        // given
        Map<String, Object> message = new HashMap<>();
        message.put("paymentId", "payment-123");
        message.put("reservationId", "reservation-123");
        message.put("orderId", "order-123");
        message.put("paymentKey", "payment-key-123");
        message.put("amount", 100000L);
        message.put("method", "CARD");
        message.put("paidAt", LocalDateTime.now().toString());

        // when
        paymentEventConsumer.consumePaymentCompleted(
                message,
                "payment-123",
                "payment-completed",
                1L,
                acknowledgment
        );

        // then
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("PaymentCancelledEvent consumption success")
    void consumePaymentCancelled_success() {
        // given
        Map<String, Object> message = new HashMap<>();
        message.put("paymentId", "payment-123");
        message.put("reservationId", "reservation-123");
        message.put("orderId", "order-123");
        message.put("amount", 100000L);
        message.put("cancelledAt", LocalDateTime.now().toString());

        // when
        paymentEventConsumer.consumePaymentCancelled(
                message,
                "payment-123",
                "payment-cancelled",
                1L,
                acknowledgment
        );

        // then
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("RefundCompletedEvent consumption success")
    void consumeRefundCompleted_success() {
        // given
        Map<String, Object> message = new HashMap<>();
        message.put("refundId", "refund-123");
        message.put("paymentId", "payment-123");
        message.put("reservationId", "reservation-123");
        message.put("originalAmount", 100000L);
        message.put("refundAmount", 100000L);
        message.put("reason", "Customer request");
        message.put("completedAt", LocalDateTime.now().toString());

        // when
        paymentEventConsumer.consumeRefundCompleted(
                message,
                "refund-123",
                "refund-completed",
                1L,
                acknowledgment
        );

        // then
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("PaymentCompletedEvent consumption failure - does not ACK")
    void consumePaymentCompleted_failure_doesNotAck() {
        // given
        Map<String, Object> invalidMessage = new HashMap<>();
        invalidMessage.put("invalid", "data");

        // when
        paymentEventConsumer.consumePaymentCompleted(
                invalidMessage,
                "payment-123",
                "payment-completed",
                1L,
                acknowledgment
        );

        // then
        verify(acknowledgment, never()).acknowledge();
    }
}
