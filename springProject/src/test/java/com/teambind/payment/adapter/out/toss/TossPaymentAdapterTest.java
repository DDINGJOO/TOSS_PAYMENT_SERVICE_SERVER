package com.teambind.payment.adapter.out.toss;

import com.teambind.payment.adapter.out.toss.dto.TossPaymentConfirmRequest;
import com.teambind.payment.adapter.out.toss.dto.TossPaymentConfirmResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class TossPaymentAdapterTest {
	
	private static final String BASE_URL = "https://api.tosspayments.com";
	private static final String SECRET_KEY = "test_secret_key";
	@Mock
	private WebClient.Builder webClientBuilder;
	@Mock
	private WebClient webClient;
	@Mock
	private WebClient.RequestBodyUriSpec requestBodyUriSpec;
	@Mock
	private WebClient.RequestBodySpec requestBodySpec;
	@Mock
	private WebClient.RequestHeadersSpec requestHeadersSpec;
	@Mock
	private WebClient.ResponseSpec responseSpec;
	private TossPaymentAdapter tossPaymentAdapter;
	
	@BeforeEach
	void setUp() {
		tossPaymentAdapter = new TossPaymentAdapter(webClientBuilder);
		ReflectionTestUtils.setField(tossPaymentAdapter, "baseUrl", BASE_URL);
		ReflectionTestUtils.setField(tossPaymentAdapter, "secretKey", SECRET_KEY);
	}
	
	@Test
	@DisplayName("토스 결제 승인 성공")
	void confirmPayment_success() {
		// given
		TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(
				"payment-key-123",
				"order-123",
				100000L
		);
		
		TossPaymentConfirmResponse expectedResponse = new TossPaymentConfirmResponse(
				"payment-key-123",
				"order-123",
				"NORMAL",
				"transaction-123",
				100000L,
				"CARD",
				"DONE",
				LocalDateTime.now()
		);
		
		String expectedAuthHeader = "Basic " + Base64.getEncoder()
				.encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));
		
		given(webClientBuilder.build()).willReturn(webClient);
		given(webClient.post()).willReturn(requestBodyUriSpec);
		given(requestBodyUriSpec.uri(BASE_URL + "/v1/payments/confirm")).willReturn(requestBodySpec);
		given(requestBodySpec.header(HttpHeaders.AUTHORIZATION, expectedAuthHeader)).willReturn(requestBodySpec);
		given(requestBodySpec.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)).willReturn(requestBodySpec);
		given(requestBodySpec.bodyValue(request)).willReturn(requestHeadersSpec);
		given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
		given(responseSpec.bodyToMono(TossPaymentConfirmResponse.class)).willReturn(Mono.just(expectedResponse));
		
		// when
		TossPaymentConfirmResponse result = tossPaymentAdapter.confirmPayment(request);
		
		// then
		assertThat(result).isNotNull();
		assertThat(result.paymentKey()).isEqualTo("payment-key-123");
		assertThat(result.orderId()).isEqualTo("order-123");
		assertThat(result.transactionId()).isEqualTo("transaction-123");
		assertThat(result.totalAmount()).isEqualTo(100000L);
		assertThat(result.method()).isEqualTo("CARD");
		assertThat(result.status()).isEqualTo("DONE");
		
		verify(webClientBuilder).build();
		verify(webClient).post();
		verify(requestBodySpec).bodyValue(request);
	}
	
	@Test
	@DisplayName("토스 결제 승인 실패 - API 오류")
	void confirmPayment_fail_apiError() {
		// given
		TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(
				"payment-key-123",
				"order-123",
				100000L
		);
		
		given(webClientBuilder.build()).willReturn(webClient);
		given(webClient.post()).willReturn(requestBodyUriSpec);
		given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
		given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
		given(requestBodySpec.bodyValue(any())).willReturn(requestHeadersSpec);
		given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
		given(responseSpec.bodyToMono(TossPaymentConfirmResponse.class))
				.willReturn(Mono.error(new RuntimeException("토스 API 오류")));
		
		// when & then
		assertThatThrownBy(() -> tossPaymentAdapter.confirmPayment(request))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("토스 결제 승인 실패");
	}
	
	@Test
	@DisplayName("Base64 인증 헤더 생성 확인")
	void confirmPayment_authHeaderEncoding() {
		// given
		TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(
				"payment-key-123",
				"order-123",
				100000L
		);
		
		TossPaymentConfirmResponse expectedResponse = new TossPaymentConfirmResponse(
				"payment-key-123",
				"order-123",
				"NORMAL",
				"transaction-123",
				100000L,
				"CARD",
				"DONE",
				LocalDateTime.now()
		);
		
		// Base64 인코딩 검증을 위한 예상 값
		String expectedEncodedKey = Base64.getEncoder()
				.encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));
		String expectedAuthHeader = "Basic " + expectedEncodedKey;
		
		given(webClientBuilder.build()).willReturn(webClient);
		given(webClient.post()).willReturn(requestBodyUriSpec);
		given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
		given(requestBodySpec.header(eq(HttpHeaders.AUTHORIZATION), eq(expectedAuthHeader))).willReturn(requestBodySpec);
		given(requestBodySpec.header(eq(HttpHeaders.CONTENT_TYPE), anyString())).willReturn(requestBodySpec);
		given(requestBodySpec.bodyValue(any())).willReturn(requestHeadersSpec);
		given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
		given(responseSpec.bodyToMono(TossPaymentConfirmResponse.class)).willReturn(Mono.just(expectedResponse));
		
		// when
		tossPaymentAdapter.confirmPayment(request);
		
		// then
		verify(requestBodySpec).header(HttpHeaders.AUTHORIZATION, expectedAuthHeader);
	}
	
	@Test
	@DisplayName("올바른 API 엔드포인트 호출 확인")
	void confirmPayment_correctEndpoint() {
		// given
		TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(
				"payment-key-123",
				"order-123",
				100000L
		);
		
		TossPaymentConfirmResponse expectedResponse = new TossPaymentConfirmResponse(
				"payment-key-123",
				"order-123",
				"NORMAL",
				"transaction-123",
				100000L,
				"CARD",
				"DONE",
				LocalDateTime.now()
		);
		
		String expectedUri = BASE_URL + "/v1/payments/confirm";
		
		given(webClientBuilder.build()).willReturn(webClient);
		given(webClient.post()).willReturn(requestBodyUriSpec);
		given(requestBodyUriSpec.uri(expectedUri)).willReturn(requestBodySpec);
		given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
		given(requestBodySpec.bodyValue(any())).willReturn(requestHeadersSpec);
		given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
		given(responseSpec.bodyToMono(TossPaymentConfirmResponse.class)).willReturn(Mono.just(expectedResponse));
		
		// when
		tossPaymentAdapter.confirmPayment(request);
		
		// then
		verify(requestBodyUriSpec).uri(expectedUri);
	}
	
	@Test
	@DisplayName("요청 본문이 올바르게 전달되는지 확인")
	void confirmPayment_requestBodyCorrect() {
		// given
		TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(
				"payment-key-456",
				"order-456",
				200000L
		);
		
		TossPaymentConfirmResponse expectedResponse = new TossPaymentConfirmResponse(
				"payment-key-456",
				"order-456",
				"NORMAL",
				"transaction-456",
				200000L,
				"EASY_PAY",
				"DONE",
				LocalDateTime.now()
		);
		
		given(webClientBuilder.build()).willReturn(webClient);
		given(webClient.post()).willReturn(requestBodyUriSpec);
		given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
		given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
		given(requestBodySpec.bodyValue(request)).willReturn(requestHeadersSpec);
		given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
		given(responseSpec.bodyToMono(TossPaymentConfirmResponse.class)).willReturn(Mono.just(expectedResponse));
		
		// when
		TossPaymentConfirmResponse result = tossPaymentAdapter.confirmPayment(request);
		
		// then
		assertThat(result.paymentKey()).isEqualTo(request.paymentKey());
		assertThat(result.orderId()).isEqualTo(request.orderId());
		assertThat(result.totalAmount()).isEqualTo(request.amount());
		verify(requestBodySpec).bodyValue(request);
	}
}
