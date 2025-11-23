package com.teambind.payment.adapter.out.toss;

import com.teambind.payment.adapter.out.toss.dto.TossPaymentConfirmRequest;
import com.teambind.payment.adapter.out.toss.dto.TossPaymentConfirmResponse;
import com.teambind.payment.application.port.out.TossPaymentClient;
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
public class TossPaymentAdapter implements TossPaymentClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${toss.payments.api.base-url}")
    private String baseUrl;

    @Value("${toss.payments.api.secret-key}")
    private String secretKey;

    @Override
    public TossPaymentConfirmResponse confirmPayment(TossPaymentConfirmRequest request) {
        log.info("토스 결제 승인 요청 - orderId: {}, amount: {}", request.orderId(), request.amount());

        String encodedKey = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        try {
            TossPaymentConfirmResponse response = webClientBuilder.build()
                    .post()
                    .uri(baseUrl + "/v1/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(TossPaymentConfirmResponse.class)
                    .block();

            log.info("토스 결제 승인 성공 - transactionId: {}, method: {}",
                    response.transactionId(), response.method());

            return response;

        } catch (Exception e) {
            log.error("토스 결제 승인 실패 - orderId: {}, error: {}",
                    request.orderId(), e.getMessage(), e);
            throw new RuntimeException("토스 결제 승인 실패: " + e.getMessage(), e);
        }
    }
}