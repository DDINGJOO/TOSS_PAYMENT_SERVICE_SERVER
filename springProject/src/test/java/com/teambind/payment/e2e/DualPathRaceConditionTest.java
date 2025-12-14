package com.teambind.payment.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dual Path Architecture Race Condition 테스트
 * - API와 Kafka 이벤트가 동시에 들어올 때 중복 생성 방지 확인
 * - DB unique 제약조건 + Exception handling 검증
 */
@DisplayName("Dual Path Race Condition 테스트")
class DualPathRaceConditionTest extends AbstractE2ETest {
	
	@Test
	@DisplayName("API와 Kafka 이벤트 동시 처리 - 중복 방지 (API 먼저)")
	void dualPath_API_First_Then_Kafka() throws Exception {
		// Given
		String reservationId = "RSV-RACE-001";
		Long amount = 50000L;
		LocalDateTime checkInDate = LocalDateTime.now().plusDays(10);
		
		String prepareRequest = String.format("""
				{
				    "reservationId": "%s",
				    "amount": %d,
				    "checkInDate": "%s"
				}
				""", reservationId, amount, checkInDate);
		
		// When 1: API로 먼저 결제 준비
		mockMvc.perform(post("/api/v1/payments")
						.contentType(MediaType.APPLICATION_JSON)
						.content(prepareRequest))
				.andDo(print())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PREPARED"));
		
		// When 2: Kafka 이벤트 발행 (동일한 reservationId)
		String eventPayload = String.format("""
				{
				    "reservationId": "%s",
				    "amount": %d,
				    "checkInDate": "%s"
				}
				""", reservationId, amount, checkInDate);
		
		kafkaTemplate.send("reservation-confirmed", eventPayload);
		
		// Then: Payment는 정확히 1개만 존재해야 함
		await().atMost(10, TimeUnit.SECONDS)
				.untilAsserted(() -> {
					Integer count = jdbcTemplate.queryForObject(
							"SELECT COUNT(*) FROM payments WHERE reservation_id = ?",
							Integer.class,
							reservationId
					);
					assertThat(count).isEqualTo(1);
				});
		
		// 상태도 PREPARED여야 함
		String status = jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE reservation_id = ?",
				String.class,
				reservationId
		);
		assertThat(status).isEqualTo("PREPARED");
	}
	
	@Test
	@DisplayName("API와 Kafka 이벤트 동시 처리 - 중복 방지 (Kafka 먼저)")
	void dualPath_Kafka_First_Then_API() throws Exception {
		// Given
		String reservationId = "RSV-RACE-002";
		Long amount = 75000L;
		LocalDateTime checkInDate = LocalDateTime.now().plusDays(5);
		
		// When 1: Kafka 이벤트 먼저 발행
		String eventPayload = String.format("""
				{
				    "reservationId": "%s",
				    "amount": %d,
				    "checkInDate": "%s"
				}
				""", reservationId, amount, checkInDate);
		
		kafkaTemplate.send("reservation-confirmed", eventPayload);
		
		// 이벤트가 처리될 때까지 대기
		await().atMost(10, TimeUnit.SECONDS)
				.untilAsserted(() -> {
					Integer count = jdbcTemplate.queryForObject(
							"SELECT COUNT(*) FROM payments WHERE reservation_id = ?",
							Integer.class,
							reservationId
					);
					assertThat(count).isEqualTo(1);
				});
		
		// When 2: API로 결제 준비 시도 (동일한 reservationId)
		String prepareRequest = String.format("""
				{
				    "reservationId": "%s",
				    "amount": %d,
				    "checkInDate": "%s"
				}
				""", reservationId, amount, checkInDate);
		
		// API는 성공적으로 응답 (멱등성 - 기존 Payment 반환)
		mockMvc.perform(post("/api/v1/payments")
						.contentType(MediaType.APPLICATION_JSON)
						.content(prepareRequest))
				.andDo(print())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PREPARED"))
				.andExpect(jsonPath("$.reservationId").value(reservationId));
		
		// Then: Payment는 여전히 1개만 존재
		Integer finalCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payments WHERE reservation_id = ?",
				Integer.class,
				reservationId
		);
		assertThat(finalCount).isEqualTo(1);
	}
	
	@Test
	@DisplayName("API와 Kafka 이벤트 거의 동시 처리 - Race Condition 시뮬레이션")
	void dualPath_Concurrent_Requests() throws Exception {
		// Given
		String reservationId = "RSV-RACE-003";
		Long amount = 100000L;
		LocalDateTime checkInDate = LocalDateTime.now().plusDays(7);
		
		String prepareRequest = String.format("""
				{
				    "reservationId": "%s",
				    "amount": %d,
				    "checkInDate": "%s"
				}
				""", reservationId, amount, checkInDate);
		
		String eventPayload = String.format("""
				{
				    "reservationId": "%s",
				    "amount": %d,
				    "checkInDate": "%s"
				}
				""", reservationId, amount, checkInDate);
		
		// When: API와 Kafka 이벤트를 동시에 발행
		CompletableFuture<Void> apiFuture = CompletableFuture.runAsync(() -> {
			try {
				mockMvc.perform(post("/api/v1/payments")
								.contentType(MediaType.APPLICATION_JSON)
								.content(prepareRequest))
						.andExpect(status().isCreated());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		
		CompletableFuture<Void> kafkaFuture = CompletableFuture.runAsync(() -> {
			kafkaTemplate.send("reservation-confirmed", eventPayload);
		});
		
		// 두 요청 모두 완료 대기
		CompletableFuture.allOf(apiFuture, kafkaFuture).join();
		
		// Then: Payment는 정확히 1개만 생성되어야 함
		await().atMost(15, TimeUnit.SECONDS)
				.untilAsserted(() -> {
					Integer count = jdbcTemplate.queryForObject(
							"SELECT COUNT(*) FROM payments WHERE reservation_id = ?",
							Integer.class,
							reservationId
					);
					assertThat(count).isEqualTo(1);
				});
		
		// 상태 확인
		String status = jdbcTemplate.queryForObject(
				"SELECT status FROM payments WHERE reservation_id = ?",
				String.class,
				reservationId
		);
		assertThat(status).isEqualTo("PREPARED");
	}
	
	@Test
	@DisplayName("여러 개의 동시 API 요청 - 멱등성 보장")
	void multipleAPI_Concurrent_Idempotency() throws Exception {
		// Given
		String reservationId = "RSV-RACE-004";
		Long amount = 80000L;
		LocalDateTime checkInDate = LocalDateTime.now().plusDays(3);
		
		String prepareRequest = String.format("""
				{
				    "reservationId": "%s",
				    "amount": %d,
				    "checkInDate": "%s"
				}
				""", reservationId, amount, checkInDate);
		
		// When: 같은 API를 5번 동시에 호출
		CompletableFuture<?>[] futures = new CompletableFuture[5];
		for (int i = 0; i < 5; i++) {
			futures[i] = CompletableFuture.runAsync(() -> {
				try {
					mockMvc.perform(post("/api/v1/payments")
									.contentType(MediaType.APPLICATION_JSON)
									.content(prepareRequest))
							.andExpect(status().isCreated());
				} catch (Exception e) {
					// 일부 요청은 실패할 수 있음 (경합 상태)
					// 하지만 최종적으로는 1개만 저장되어야 함
				}
			});
		}
		
		CompletableFuture.allOf(futures).join();
		
		// Then: Payment는 정확히 1개만 존재
		await().atMost(10, TimeUnit.SECONDS)
				.untilAsserted(() -> {
					Integer count = jdbcTemplate.queryForObject(
							"SELECT COUNT(*) FROM payments WHERE reservation_id = ?",
							Integer.class,
							reservationId
					);
					assertThat(count).isEqualTo(1);
				});
	}
	
	@Test
	@DisplayName("다른 reservationId는 정상적으로 각각 생성")
	void differentReservations_CreatedSeparately() throws Exception {
		// Given: 3개의 서로 다른 예약
		String[] reservationIds = {"RSV-RACE-005", "RSV-RACE-006", "RSV-RACE-007"};
		Long amount = 50000L;
		LocalDateTime checkInDate = LocalDateTime.now().plusDays(5);
		
		// When: 각각 API와 Kafka로 생성
		for (String reservationId : reservationIds) {
			String prepareRequest = String.format("""
					{
					    "reservationId": "%s",
					    "amount": %d,
					    "checkInDate": "%s"
					}
					""", reservationId, amount, checkInDate);
			
			mockMvc.perform(post("/api/v1/payments")
							.contentType(MediaType.APPLICATION_JSON)
							.content(prepareRequest))
					.andExpect(status().isCreated());
		}
		
		// Then: 3개 모두 생성되어야 함
		Integer totalCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payments WHERE reservation_id IN (?, ?, ?)",
				Integer.class,
				reservationIds[0], reservationIds[1], reservationIds[2]
		);
		assertThat(totalCount).isEqualTo(3);
		
		// 각 reservationId당 정확히 1개씩
		for (String reservationId : reservationIds) {
			Integer count = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM payments WHERE reservation_id = ?",
					Integer.class,
					reservationId
			);
			assertThat(count).isEqualTo(1);
		}
	}
}
