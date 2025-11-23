package com.teambind.payment.domain;

public enum PaymentStatus {
    PREPARED,    // 결제 대기 (Kafka 이벤트로 저장된 상태)
    COMPLETED,   // 결제 완료 (Toss 승인 완료)
    FAILED,      // 결제 실패
    CANCELLED    // 결제 취소
}