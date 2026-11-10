IF NOT EXISTS (SELECT 1 FROM iam_permission WHERE code = 'PAYMENT_RECONCILE')
    INSERT INTO iam_permission(code) VALUES ('PAYMENT_RECONCILE');

IF NOT EXISTS (
    SELECT 1 FROM iam_role_permission role_permissions
    JOIN iam_role_bundle roles ON roles.id = role_permissions.role_id
    JOIN iam_permission permissions ON permissions.id = role_permissions.permission_id
    WHERE roles.code = 'OPERATIONS' AND permissions.code = 'PAYMENT_RECONCILE'
)
    INSERT INTO iam_role_permission(role_id, permission_id)
    SELECT roles.id, permissions.id FROM iam_role_bundle roles CROSS JOIN iam_permission permissions
    WHERE roles.code = 'OPERATIONS' AND permissions.code = 'PAYMENT_RECONCILE';

CREATE TABLE payment_reconciliation_command (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_payment_reconciliation_command PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_payment_reconciliation_public_id UNIQUE,
    payment_attempt_public_id UNIQUEIDENTIFIER NOT NULL,
    actor_account_public_id UNIQUEIDENTIFIER NOT NULL,
    idempotency_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    provider_request_id VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    provider_outcome VARCHAR(24) NULL,
    provider_response_id VARCHAR(32) NULL,
    provider_response_code VARCHAR(8) NULL,
    provider_transaction_status VARCHAR(8) NULL,
    provider_transaction_no VARCHAR(32) NULL,
    provider_paid_at DATETIME2(6) NULL,
    provider_evidence_hash VARCHAR(64) NULL,
    previous_attempt_status VARCHAR(24) NULL,
    current_attempt_status VARCHAR(24) NULL,
    local_transition_applied BIT NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_payment_reconciliation_version DEFAULT 0,
    created_at DATETIME2(6) NOT NULL,
    resolved_at DATETIME2(6) NULL,
    CONSTRAINT FK_payment_reconciliation_attempt FOREIGN KEY (payment_attempt_public_id) REFERENCES payment_attempt(public_id),
    CONSTRAINT FK_payment_reconciliation_actor FOREIGN KEY (actor_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT UQ_payment_reconciliation_actor_key UNIQUE (actor_account_public_id, idempotency_key),
    CONSTRAINT UQ_payment_reconciliation_provider_request UNIQUE (provider_request_id),
    CONSTRAINT CK_payment_reconciliation_status CHECK (status IN ('QUERYING','PROVIDER_UNAVAILABLE','EVIDENCE_RECORDED','MISMATCH','COMPLETED')),
    CONSTRAINT CK_payment_reconciliation_time CHECK (
        (status IN ('QUERYING','EVIDENCE_RECORDED') AND resolved_at IS NULL)
        OR (status IN ('PROVIDER_UNAVAILABLE','MISMATCH','COMPLETED') AND resolved_at IS NOT NULL)
    ),
    CONSTRAINT CK_payment_reconciliation_evidence CHECK (
        (status IN ('QUERYING','PROVIDER_UNAVAILABLE','MISMATCH') AND provider_outcome IS NULL AND provider_evidence_hash IS NULL)
        OR (status IN ('EVIDENCE_RECORDED','COMPLETED') AND provider_outcome IS NOT NULL AND LEN(provider_evidence_hash) = 64)
    )
);

CREATE INDEX IX_payment_reconciliation_attempt ON payment_reconciliation_command(payment_attempt_public_id, status);
