CREATE TABLE payment (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_payment PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_payment_public_id UNIQUE,
    order_id BIGINT NOT NULL CONSTRAINT UQ_payment_order UNIQUE,
    currency VARCHAR(3) NOT NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_payment_version DEFAULT 0,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT FK_payment_order FOREIGN KEY (order_id) REFERENCES commerce_order(id),
    CONSTRAINT CK_payment_currency CHECK (currency = 'VND')
);

CREATE TABLE payment_attempt (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_payment_attempt PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_payment_attempt_public_id UNIQUE,
    payment_id BIGINT NOT NULL,
    owner_account_public_id UNIQUEIDENTIFIER NOT NULL,
    idempotency_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    status VARCHAR(16) NOT NULL,
    amount DECIMAL(19,0) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_payment_attempt_version DEFAULT 0,
    created_at DATETIME2(6) NOT NULL,
    cancelled_at DATETIME2(6) NULL,
    CONSTRAINT FK_payment_attempt_payment FOREIGN KEY (payment_id) REFERENCES payment(id),
    CONSTRAINT FK_payment_attempt_owner FOREIGN KEY (owner_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT UQ_payment_attempt_owner_key UNIQUE (owner_account_public_id, idempotency_key),
    CONSTRAINT CK_payment_attempt_status CHECK (status IN ('PENDING', 'CANCELLED')),
    CONSTRAINT CK_payment_attempt_amount CHECK (amount > 0),
    CONSTRAINT CK_payment_attempt_currency CHECK (currency = 'VND'),
    CONSTRAINT CK_payment_attempt_cancelled_time CHECK ((status = 'PENDING' AND cancelled_at IS NULL) OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL))
);

CREATE UNIQUE INDEX UX_payment_attempt_one_pending
    ON payment_attempt(payment_id)
    WHERE status = 'PENDING';

INSERT INTO iam_permission(code) VALUES ('PAYMENT_INITIATE');

INSERT INTO iam_role_permission(role_id, permission_id)
SELECT roles.id, permissions.id
FROM iam_role_bundle roles
CROSS JOIN iam_permission permissions
WHERE roles.code = 'CUSTOMER' AND permissions.code = 'PAYMENT_INITIATE';
