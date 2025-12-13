-- payments 테이블
CREATE TABLE payments (
    payment_id VARCHAR(50) PRIMARY KEY,
    reservation_id VARCHAR(50) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KRW',
    method VARCHAR(20),
    status VARCHAR(20) NOT NULL,
    order_id VARCHAR(100),
    payment_key VARCHAR(200),
    transaction_id VARCHAR(100),
    check_in_date DATETIME NOT NULL,
    idempotency_key VARCHAR(100) UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME,
    cancelled_at DATETIME,
    failure_reason TEXT,
    CONSTRAINT chk_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- refunds 테이블
CREATE TABLE refunds (
    refund_id VARCHAR(50) PRIMARY KEY,
    payment_id VARCHAR(50) NOT NULL,
    refund_amount DECIMAL(15,2) NOT NULL,
    original_amount DECIMAL(15,2) NOT NULL,
    refund_rate DECIMAL(3,2) NOT NULL,
    commission_free BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    toss_refund_key VARCHAR(200),
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    failure_reason TEXT,
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(payment_id) ON DELETE RESTRICT,
    CONSTRAINT chk_refund_amount CHECK (refund_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- payment_events 테이블 (Outbox Pattern)
CREATE TABLE payment_events (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id VARCHAR(50) NOT NULL COMMENT 'paymentId 또는 reservationId',
    event_type VARCHAR(50) NOT NULL COMMENT 'PAYMENT_COMPLETED, PAYMENT_FAILED, REFUND_COMPLETED',
    payload TEXT NOT NULL COMMENT 'JSON 형식의 이벤트 데이터',
    status VARCHAR(20) NOT NULL COMMENT 'PENDING, PUBLISHED, FAILED',
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at DATETIME,
    error_message TEXT COMMENT '발행 실패 에러 메시지'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;