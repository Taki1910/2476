ALTER TABLE payment_attempt ADD resolved_at DATETIME2(6) NULL;

GO

ALTER TABLE payment_attempt DROP CONSTRAINT CK_payment_attempt_cancelled_time;
ALTER TABLE payment_attempt DROP CONSTRAINT CK_payment_attempt_status;

ALTER TABLE payment_attempt ADD CONSTRAINT CK_payment_attempt_status
    CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED'));

ALTER TABLE payment_attempt ADD CONSTRAINT CK_payment_attempt_lifecycle_time
    CHECK (
        (status = 'PENDING' AND cancelled_at IS NULL AND resolved_at IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND resolved_at IS NULL)
        OR (status IN ('SUCCEEDED', 'FAILED') AND cancelled_at IS NULL AND resolved_at IS NOT NULL)
    );

ALTER TABLE commerce_order ADD paid_at DATETIME2(6) NULL;

GO

ALTER TABLE commerce_order DROP CONSTRAINT CK_commerce_order_cancelled_time;
ALTER TABLE commerce_order DROP CONSTRAINT CK_commerce_order_status;

ALTER TABLE commerce_order ADD CONSTRAINT CK_commerce_order_status
    CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED'));

ALTER TABLE commerce_order ADD CONSTRAINT CK_commerce_order_lifecycle_time
    CHECK (
        (status = 'PENDING_PAYMENT' AND paid_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'PAID' AND paid_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND paid_at IS NULL AND cancelled_at IS NOT NULL)
    );

ALTER TABLE inventory_reservation ADD consumed_at DATETIME2(6) NULL;

GO

ALTER TABLE inventory_reservation DROP CONSTRAINT CK_inventory_reservation_lifecycle_time;
ALTER TABLE inventory_reservation DROP CONSTRAINT CK_inventory_reservation_status;

ALTER TABLE inventory_reservation ADD CONSTRAINT CK_inventory_reservation_status
    CHECK (status IN ('ACTIVE', 'ADOPTED', 'RELEASED', 'CONSUMED'));

ALTER TABLE inventory_reservation ADD CONSTRAINT CK_inventory_reservation_lifecycle_time
    CHECK (
        (status = 'ACTIVE' AND adopted_at IS NULL AND released_at IS NULL AND consumed_at IS NULL)
        OR (status = 'ADOPTED' AND adopted_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NULL)
        OR (status = 'RELEASED' AND released_at IS NOT NULL AND consumed_at IS NULL)
        OR (status = 'CONSUMED' AND adopted_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NOT NULL)
    );

CREATE TABLE payment_provider_event (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_payment_provider_event PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_payment_provider_event_public_id UNIQUE,
    provider_account_public_id UNIQUEIDENTIFIER NOT NULL,
    provider_event_id NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    payment_attempt_public_id UNIQUEIDENTIFIER NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    disposition VARCHAR(16) NOT NULL,
    attempt_status VARCHAR(16) NOT NULL,
    order_status VARCHAR(24) NOT NULL,
    rejection_reason VARCHAR(32) NULL,
    received_at DATETIME2(6) NOT NULL,
    applied_at DATETIME2(6) NULL,
    CONSTRAINT FK_payment_provider_event_provider FOREIGN KEY (provider_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT FK_payment_provider_event_attempt FOREIGN KEY (payment_attempt_public_id) REFERENCES payment_attempt(public_id),
    CONSTRAINT UQ_payment_provider_event_scope UNIQUE (provider_account_public_id, provider_event_id),
    CONSTRAINT CK_payment_provider_event_id CHECK (LEN(provider_event_id) > 0),
    CONSTRAINT CK_payment_provider_event_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT CK_payment_provider_event_disposition CHECK (disposition IN ('APPLIED', 'REJECTED')),
    CONSTRAINT CK_payment_provider_event_attempt_status CHECK (attempt_status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT CK_payment_provider_event_order_status CHECK (order_status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED')),
    CONSTRAINT CK_payment_provider_event_application CHECK (
        (disposition = 'APPLIED' AND applied_at IS NOT NULL AND rejection_reason IS NULL)
        OR (disposition = 'REJECTED' AND applied_at IS NULL AND rejection_reason IS NOT NULL)
    )
);
