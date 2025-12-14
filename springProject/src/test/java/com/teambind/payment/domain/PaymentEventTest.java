package com.teambind.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentEvent 도메인 테스트")
class PaymentEventTest {
	
	@Test
	@DisplayName("이벤트 생성 - 성공")
	void create_Success() {
		// Given
		String aggregateId = "PAY-12345678";
		EventType eventType = EventType.PAYMENT_COMPLETED;
		String payload = "{\"reservationId\":\"RSV-001\",\"amount\":50000}";
		
		// When
		PaymentEvent event = PaymentEvent.create(aggregateId, eventType, payload);
		
		// Then
		assertThat(event.getAggregateId()).isEqualTo(aggregateId);
		assertThat(event.getEventType()).isEqualTo(eventType);
		assertThat(event.getPayload()).isEqualTo(payload);
		assertThat(event.getStatus()).isEqualTo(EventStatus.PENDING);
		assertThat(event.getRetryCount()).isEqualTo(0);
		assertThat(event.getCreatedAt()).isNotNull();
	}
	
	@Test
	@DisplayName("이벤트 생성 - aggregateId가 null이면 예외 발생")
	void create_NullAggregateId_ThrowsException() {
		// Given
		String aggregateId = null;
		EventType eventType = EventType.PAYMENT_COMPLETED;
		String payload = "{\"reservationId\":\"RSV-001\"}";
		
		// When & Then
		assertThatThrownBy(() -> PaymentEvent.create(aggregateId, eventType, payload))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Aggregate ID는 필수입니다");
	}
	
	@Test
	@DisplayName("이벤트 생성 - eventType이 null이면 예외 발생")
	void create_NullEventType_ThrowsException() {
		// Given
		String aggregateId = "PAY-12345678";
		EventType eventType = null;
		String payload = "{\"reservationId\":\"RSV-001\"}";
		
		// When & Then
		assertThatThrownBy(() -> PaymentEvent.create(aggregateId, eventType, payload))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Event Type은 필수입니다");
	}
	
	@Test
	@DisplayName("이벤트 생성 - payload가 null이면 예외 발생")
	void create_NullPayload_ThrowsException() {
		// Given
		String aggregateId = "PAY-12345678";
		EventType eventType = EventType.PAYMENT_COMPLETED;
		String payload = null;
		
		// When & Then
		assertThatThrownBy(() -> PaymentEvent.create(aggregateId, eventType, payload))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Payload는 필수입니다");
	}
	
	@Test
	@DisplayName("이벤트 발행 완료 처리 - 성공")
	void markAsPublished_Success() {
		// Given
		PaymentEvent event = PaymentEvent.create(
				"PAY-12345678",
				EventType.PAYMENT_COMPLETED,
				"{\"reservationId\":\"RSV-001\"}"
		);
		
		// When
		event.markAsPublished();
		
		// Then
		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.getPublishedAt()).isNotNull();
	}
	
	@Test
	@DisplayName("이벤트 발행 완료 처리 - 이미 발행된 이벤트는 예외 발생")
	void markAsPublished_AlreadyPublished_ThrowsException() {
		// Given
		PaymentEvent event = PaymentEvent.create(
				"PAY-12345678",
				EventType.PAYMENT_COMPLETED,
				"{\"reservationId\":\"RSV-001\"}"
		);
		event.markAsPublished();
		
		// When & Then
		assertThatThrownBy(event::markAsPublished)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("이미 발행된 이벤트입니다");
	}
	
	@Test
	@DisplayName("이벤트 실패 처리 - 성공")
	void markAsFailed_Success() {
		// Given
		PaymentEvent event = PaymentEvent.create(
				"PAY-12345678",
				EventType.PAYMENT_COMPLETED,
				"{\"reservationId\":\"RSV-001\"}"
		);
		String errorMessage = "Kafka connection timeout";
		
		// When
		event.markAsFailed(errorMessage);
		
		// Then
		assertThat(event.getStatus()).isEqualTo(EventStatus.FAILED);
		assertThat(event.getErrorMessage()).isEqualTo(errorMessage);
		assertThat(event.getRetryCount()).isEqualTo(1);
	}
	
	@Test
	@DisplayName("이벤트 실패 처리 - errorMessage가 null이면 예외 발생")
	void markAsFailed_NullErrorMessage_ThrowsException() {
		// Given
		PaymentEvent event = PaymentEvent.create(
				"PAY-12345678",
				EventType.PAYMENT_COMPLETED,
				"{\"reservationId\":\"RSV-001\"}"
		);
		String errorMessage = null;
		
		// When & Then
		assertThatThrownBy(() -> event.markAsFailed(errorMessage))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Error message는 필수입니다");
	}
	
	@Test
	@DisplayName("이벤트 재시도 초기화 - 성공")
	void resetForRetry_Success() {
		// Given
		PaymentEvent event = PaymentEvent.create(
				"PAY-12345678",
				EventType.PAYMENT_COMPLETED,
				"{\"reservationId\":\"RSV-001\"}"
		);
		event.markAsFailed("Kafka connection timeout");
		
		// When
		event.resetForRetry();
		
		// Then
		assertThat(event.getStatus()).isEqualTo(EventStatus.PENDING);
		assertThat(event.getErrorMessage()).isNull();
		assertThat(event.getRetryCount()).isEqualTo(1); // 재시도 횟수는 유지
	}
	
	@Test
	@DisplayName("이벤트 재시도 초기화 - 이미 발행된 이벤트는 예외 발생")
	void resetForRetry_AlreadyPublished_ThrowsException() {
		// Given
		PaymentEvent event = PaymentEvent.create(
				"PAY-12345678",
				EventType.PAYMENT_COMPLETED,
				"{\"reservationId\":\"RSV-001\"}"
		);
		event.markAsPublished();
		
		// When & Then
		assertThatThrownBy(event::resetForRetry)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("이미 발행된 이벤트는 재시도할 수 없습니다");
	}
	
	@Test
	@DisplayName("상태 확인 - isPending")
	void isPending_Success() {
		// Given
		PaymentEvent event = PaymentEvent.create(
				"PAY-12345678",
				EventType.PAYMENT_COMPLETED,
				"{\"reservationId\":\"RSV-001\"}"
		);
		
		// When & Then
		assertThat(event.isPending()).isTrue();
		assertThat(event.isPublished()).isFalse();
		assertThat(event.isFailed()).isFalse();
	}
	
	@Test
	@DisplayName("상태 확인 - isPublished")
	void isPublished_Success() {
		// Given
		PaymentEvent event = PaymentEvent.create(
				"PAY-12345678",
				EventType.PAYMENT_COMPLETED,
				"{\"reservationId\":\"RSV-001\"}"
		);
		event.markAsPublished();
		
		// When & Then
		assertThat(event.isPublished()).isTrue();
		assertThat(event.isPending()).isFalse();
		assertThat(event.isFailed()).isFalse();
	}
	
	@Test
	@DisplayName("상태 확인 - isFailed")
	void isFailed_Success() {
		// Given
		PaymentEvent event = PaymentEvent.create(
				"PAY-12345678",
				EventType.PAYMENT_COMPLETED,
				"{\"reservationId\":\"RSV-001\"}"
		);
		event.markAsFailed("Kafka connection timeout");
		
		// When & Then
		assertThat(event.isFailed()).isTrue();
		assertThat(event.isPending()).isFalse();
		assertThat(event.isPublished()).isFalse();
	}
	
	@Test
	@DisplayName("재시도 가능 여부 - 실패 상태이고 최대 재시도 횟수 미만이면 true")
	void canRetry_FailedAndBelowMaxRetry_ReturnsTrue() {
		// Given
		PaymentEvent event = PaymentEvent.create(
				"PAY-12345678",
				EventType.PAYMENT_COMPLETED,
				"{\"reservationId\":\"RSV-001\"}"
		);
		event.markAsFailed("Kafka connection timeout");
		int maxRetryCount = 3;
		
		// When & Then
		assertThat(event.canRetry(maxRetryCount)).isTrue();
	}
	
	@Test
	@DisplayName("재시도 가능 여부 - 최대 재시도 횟수 도달하면 false")
	void canRetry_ReachedMaxRetry_ReturnsFalse() {
		// Given
		PaymentEvent event = PaymentEvent.create(
				"PAY-12345678",
				EventType.PAYMENT_COMPLETED,
				"{\"reservationId\":\"RSV-001\"}"
		);
		event.markAsFailed("Error 1");
		event.markAsFailed("Error 2");
		event.markAsFailed("Error 3");
		int maxRetryCount = 3;
		
		// When & Then
		assertThat(event.canRetry(maxRetryCount)).isFalse();
		assertThat(event.getRetryCount()).isEqualTo(3);
	}
	
	@Test
	@DisplayName("재시도 가능 여부 - 발행 완료 상태이면 false")
	void canRetry_PublishedStatus_ReturnsFalse() {
		// Given
		PaymentEvent event = PaymentEvent.create(
				"PAY-12345678",
				EventType.PAYMENT_COMPLETED,
				"{\"reservationId\":\"RSV-001\"}"
		);
		event.markAsPublished();
		int maxRetryCount = 3;
		
		// When & Then
		assertThat(event.canRetry(maxRetryCount)).isFalse();
	}
	
	@Test
	@DisplayName("전체 이벤트 플로우 - 성공 시나리오")
	void fullEventFlow_Success() {
		// Given
		String aggregateId = "PAY-12345678";
		EventType eventType = EventType.PAYMENT_COMPLETED;
		String payload = "{\"reservationId\":\"RSV-001\",\"amount\":50000}";
		
		// When - 이벤트 생성
		PaymentEvent event = PaymentEvent.create(aggregateId, eventType, payload);
		assertThat(event.getStatus()).isEqualTo(EventStatus.PENDING);
		
		// When - 발행 완료
		event.markAsPublished();
		
		// Then
		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.getPublishedAt()).isNotNull();
		assertThat(event.isPublished()).isTrue();
	}
	
	@Test
	@DisplayName("전체 이벤트 플로우 - 재시도 시나리오")
	void fullEventFlow_RetryScenario() {
		// Given
		PaymentEvent event = PaymentEvent.create(
				"PAY-12345678",
				EventType.PAYMENT_COMPLETED,
				"{\"reservationId\":\"RSV-001\"}"
		);
		
		// When - 첫 번째 실패
		event.markAsFailed("Network error");
		assertThat(event.getStatus()).isEqualTo(EventStatus.FAILED);
		assertThat(event.getRetryCount()).isEqualTo(1);
		
		// When - 재시도 초기화
		event.resetForRetry();
		assertThat(event.getStatus()).isEqualTo(EventStatus.PENDING);
		
		// When - 두 번째 실패
		event.markAsFailed("Timeout");
		assertThat(event.getRetryCount()).isEqualTo(2);
		
		// When - 재시도 초기화 및 성공
		event.resetForRetry();
		event.markAsPublished();
		
		// Then
		assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
		assertThat(event.getRetryCount()).isEqualTo(2);
		assertThat(event.isPublished()).isTrue();
	}
}
