package com.teambind.payment.adapter.out.persistence;

import com.teambind.payment.domain.EventStatus;
import com.teambind.payment.domain.EventType;
import com.teambind.payment.domain.PaymentEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PaymentEventRepositoryAdapter.class)
@DisplayName("PaymentEventRepositoryAdapter 테스트")
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaymentEventRepositoryAdapterTest {

    @Autowired
    private PaymentEventRepositoryAdapter eventRepository;

    @Test
    @DisplayName("이벤트 저장 및 조회 - 성공")
    void save_AndFindById_Success() {
        // Given
        PaymentEvent event = PaymentEvent.create(
                "PAY-001",
                EventType.PAYMENT_COMPLETED,
                "{\"reservationId\":\"RSV-001\",\"amount\":50000}"
        );

        // When
        PaymentEvent savedEvent = eventRepository.save(event);
        Optional<PaymentEvent> foundEvent = eventRepository.findById(savedEvent.getEventId());

        // Then
        assertThat(foundEvent).isPresent();
        assertThat(foundEvent.get().getEventId()).isEqualTo(savedEvent.getEventId());
        assertThat(foundEvent.get().getAggregateId()).isEqualTo("PAY-001");
        assertThat(foundEvent.get().getEventType()).isEqualTo(EventType.PAYMENT_COMPLETED);
        assertThat(foundEvent.get().getStatus()).isEqualTo(EventStatus.PENDING);
    }

    @Test
    @DisplayName("발행 대기중인 이벤트 조회 - 성공")
    void findPendingEvents_Success() {
        // Given
        PaymentEvent event1 = PaymentEvent.create("PAY-001", EventType.PAYMENT_COMPLETED, "{\"data\":\"test1\"}");
        PaymentEvent event2 = PaymentEvent.create("PAY-002", EventType.PAYMENT_COMPLETED, "{\"data\":\"test2\"}");
        PaymentEvent event3 = PaymentEvent.create("PAY-003", EventType.PAYMENT_COMPLETED, "{\"data\":\"test3\"}");

        eventRepository.save(event1);
        eventRepository.save(event2);
        eventRepository.save(event3);

        event3.markAsPublished();
        eventRepository.save(event3);

        // When
        List<PaymentEvent> pendingEvents = eventRepository.findPendingEvents(10);

        // Then
        assertThat(pendingEvents).hasSize(2);
        assertThat(pendingEvents).extracting("status").containsOnly(EventStatus.PENDING);
    }

    @Test
    @DisplayName("재시도 가능한 실패 이벤트 조회 - 성공")
    void findFailedEventsForRetry_Success() {
        // Given
        PaymentEvent event1 = PaymentEvent.create("PAY-001", EventType.PAYMENT_COMPLETED, "{\"data\":\"test1\"}");
        PaymentEvent event2 = PaymentEvent.create("PAY-002", EventType.PAYMENT_COMPLETED, "{\"data\":\"test2\"}");
        PaymentEvent event3 = PaymentEvent.create("PAY-003", EventType.PAYMENT_COMPLETED, "{\"data\":\"test3\"}");

        event1.markAsFailed("Error 1");
        event2.markAsFailed("Error 2");
        event2.markAsFailed("Error 2-2");
        event2.markAsFailed("Error 2-3");
        event3.markAsFailed("Error 3");

        eventRepository.save(event1);
        eventRepository.save(event2);
        eventRepository.save(event3);

        // When - 최대 재시도 횟수가 3인 경우
        List<PaymentEvent> retryableEvents = eventRepository.findFailedEventsForRetry(3, 10);

        // Then
        assertThat(retryableEvents).hasSize(2); // event1(재시도 1회), event3(재시도 1회)
        assertThat(retryableEvents).extracting("status").containsOnly(EventStatus.FAILED);
        assertThat(retryableEvents).allMatch(e -> e.getRetryCount() < 3);
    }

    @Test
    @DisplayName("이벤트 상태 업데이트 - 성공")
    void updateEventStatus_Success() {
        // Given
        PaymentEvent event = PaymentEvent.create("PAY-001", EventType.PAYMENT_COMPLETED, "{\"data\":\"test\"}");
        PaymentEvent savedEvent = eventRepository.save(event);

        // When
        savedEvent.markAsPublished();
        PaymentEvent updatedEvent = eventRepository.save(savedEvent);

        // Then
        Optional<PaymentEvent> foundEvent = eventRepository.findById(updatedEvent.getEventId());
        assertThat(foundEvent).isPresent();
        assertThat(foundEvent.get().getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(foundEvent.get().getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("발행 대기중인 이벤트가 없을 때 빈 목록 반환")
    void findPendingEvents_NoPendingEvents_ReturnsEmptyList() {
        // Given
        PaymentEvent event = PaymentEvent.create("PAY-001", EventType.PAYMENT_COMPLETED, "{\"data\":\"test\"}");
        event.markAsPublished();
        eventRepository.save(event);

        // When
        List<PaymentEvent> pendingEvents = eventRepository.findPendingEvents(10);

        // Then
        assertThat(pendingEvents).isEmpty();
    }
}