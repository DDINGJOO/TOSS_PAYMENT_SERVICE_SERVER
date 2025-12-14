-- payments 테이블 인덱스
CREATE UNIQUE INDEX uk_idempotency_key ON payments (idempotency_key);
CREATE INDEX idx_reservation_id ON payments (reservation_id);
CREATE INDEX idx_status_created_at ON payments (status, created_at);

-- refunds 테이블 인덱스
CREATE INDEX idx_payment_id ON refunds (payment_id);

-- payment_events 테이블 인덱스
CREATE INDEX idx_status_created ON payment_events (status, created_at);
