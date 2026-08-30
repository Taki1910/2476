ALTER TABLE payment_attempt ADD
    provider VARCHAR(16) NULL,
    merchant_transaction_reference VARCHAR(100) NULL,
    client_ip VARCHAR(45) NULL,
    expires_at DATETIME2(6) NULL,
    provider_transaction_no VARCHAR(32) NULL,
    provider_response_code VARCHAR(8) NULL,
    provider_transaction_status VARCHAR(8) NULL,
    provider_paid_at DATETIME2(6) NULL,
    provider_evidence_hash VARCHAR(64) NULL;

GO

UPDATE payment_attempt
SET provider = 'LEGACY',
    merchant_transaction_reference = 'LEGACY-' + REPLACE(CONVERT(VARCHAR(36), public_id), '-', ''),
    client_ip = '127.0.0.1',
    expires_at = DATEADD(MINUTE, 15, created_at),
    provider_response_code = CASE WHEN status IN ('SUCCEEDED', 'FAILED') THEN 'LEGACY' ELSE NULL END,
    provider_transaction_status = CASE WHEN status IN ('SUCCEEDED', 'FAILED') THEN 'LEGACY' ELSE NULL END,
    provider_evidence_hash = CASE WHEN status IN ('SUCCEEDED', 'FAILED') THEN REPLICATE('0', 64) ELSE NULL END;

ALTER TABLE payment_attempt ALTER COLUMN provider VARCHAR(16) NOT NULL;
ALTER TABLE payment_attempt ALTER COLUMN merchant_transaction_reference VARCHAR(100) NOT NULL;
ALTER TABLE payment_attempt ALTER COLUMN client_ip VARCHAR(45) NOT NULL;
ALTER TABLE payment_attempt ALTER COLUMN expires_at DATETIME2(6) NOT NULL;

ALTER TABLE payment_attempt DROP CONSTRAINT CK_payment_attempt_lifecycle_time;
ALTER TABLE payment_attempt DROP CONSTRAINT CK_payment_attempt_status;

ALTER TABLE payment_attempt ADD CONSTRAINT CK_payment_attempt_status
    CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED', 'REVIEW_REQUIRED'));

ALTER TABLE payment_attempt ADD CONSTRAINT CK_payment_attempt_provider
    CHECK (provider IN ('VNPAY', 'LEGACY'));

ALTER TABLE payment_attempt ADD CONSTRAINT CK_payment_attempt_expiry
    CHECK (expires_at > created_at);

ALTER TABLE payment_attempt ADD CONSTRAINT CK_payment_attempt_lifecycle_time
    CHECK (
        (status = 'PENDING' AND cancelled_at IS NULL AND resolved_at IS NULL
            AND provider_response_code IS NULL AND provider_transaction_status IS NULL AND provider_evidence_hash IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND resolved_at IS NULL
            AND provider_response_code IS NULL AND provider_transaction_status IS NULL AND provider_evidence_hash IS NULL)
        OR (status = 'EXPIRED' AND cancelled_at IS NULL AND resolved_at IS NOT NULL
            AND provider_response_code IS NULL AND provider_transaction_status IS NULL AND provider_evidence_hash IS NULL)
        OR (status IN ('SUCCEEDED', 'FAILED', 'REVIEW_REQUIRED') AND cancelled_at IS NULL AND resolved_at IS NOT NULL
            AND provider_response_code IS NOT NULL AND provider_transaction_status IS NOT NULL AND provider_evidence_hash IS NOT NULL)
    );

ALTER TABLE payment_attempt ADD CONSTRAINT UQ_payment_attempt_merchant_reference
    UNIQUE (merchant_transaction_reference);

CREATE UNIQUE INDEX UX_payment_attempt_provider_transaction
    ON payment_attempt(provider, provider_transaction_no)
    WHERE provider_transaction_no IS NOT NULL;

ALTER TABLE inventory_reservation ADD committed_at DATETIME2(6) NULL;

GO

ALTER TABLE inventory_reservation DROP CONSTRAINT CK_inventory_reservation_lifecycle_time;
ALTER TABLE inventory_reservation DROP CONSTRAINT CK_inventory_reservation_status;

ALTER TABLE inventory_reservation ADD CONSTRAINT CK_inventory_reservation_status
    CHECK (status IN ('ACTIVE', 'ADOPTED', 'RELEASED', 'CONSUMED', 'EXPIRED', 'COMMITTED'));

ALTER TABLE inventory_reservation ADD CONSTRAINT CK_inventory_reservation_lifecycle_time
    CHECK (
        (status = 'ACTIVE' AND adopted_at IS NULL AND released_at IS NULL AND consumed_at IS NULL AND expired_at IS NULL AND committed_at IS NULL)
        OR (status = 'ADOPTED' AND adopted_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NULL AND expired_at IS NULL AND committed_at IS NULL)
        OR (status = 'RELEASED' AND released_at IS NOT NULL AND consumed_at IS NULL AND expired_at IS NULL AND committed_at IS NULL)
        OR (status = 'CONSUMED' AND adopted_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NOT NULL AND expired_at IS NULL AND committed_at IS NULL)
        OR (status = 'EXPIRED' AND expires_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NULL AND expired_at IS NOT NULL AND committed_at IS NULL)
        OR (status = 'COMMITTED' AND adopted_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NULL AND expired_at IS NULL AND committed_at IS NOT NULL)
    );

ALTER TABLE payment_provider_event DROP CONSTRAINT CK_payment_provider_event_attempt_status;
ALTER TABLE payment_provider_event ADD CONSTRAINT CK_payment_provider_event_attempt_status
    CHECK (attempt_status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED', 'REVIEW_REQUIRED'));
