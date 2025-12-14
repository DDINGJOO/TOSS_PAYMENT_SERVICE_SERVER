package com.teambind.payment.adapter.out.toss;

import com.teambind.payment.adapter.out.toss.dto.TossRefundRequest;
import com.teambind.payment.adapter.out.toss.dto.TossRefundResponse;
import com.teambind.payment.application.port.out.TossRefundClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class TossRefundAdapter implements TossRefundClient {
	
	private final WebClient.Builder webClientBuilder;
	
	@Value("${toss.payments.api.base-url}")
	private String baseUrl;
	
	@Value("${toss.payments.api.secret-key}")
	private String secretKey;
	
	@Override
	public TossRefundResponse cancelPayment(String paymentKey, TossRefundRequest request) {
		log.info("토스 결제 취소(환불) 요청 - paymentKey: {}, cancelAmount: {}",
				paymentKey, request.cancelAmount());
		
		String encodedKey = Base64.getEncoder()
				.encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
		
		try {
			TossRefundResponse response = webClientBuilder.build()
					.post()
					.uri(baseUrl + "/v1/payments/" + paymentKey + "/cancel")
					.header(HttpHeaders.AUTHORIZATION, "Basic " + encodedKey)
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.bodyValue(request)
					.retrieve()
					.bodyToMono(TossRefundResponse.class)
					.block();
			
			log.info("토스 결제 취소(환불) 성공 - transactionId: {}, cancelAmount: {}",
					response.transactionId(), response.cancelAmount());
			
			return response;
			
		} catch (Exception e) {
			log.error("토스 결제 취소(환불) 실패 - paymentKey: {}, error: {}",
					paymentKey, e.getMessage(), e);
			throw new RuntimeException("토스 결제 취소(환불) 실패: " + e.getMessage(), e);
		}
	}
}
