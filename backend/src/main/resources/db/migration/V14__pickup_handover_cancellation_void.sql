ALTER TABLE commerce_order_item ADD public_id UNIQUEIDENTIFIER NULL;

GO

UPDATE commerce_order_item SET public_id = NEWID() WHERE public_id IS NULL;
ALTER TABLE commerce_order_item ALTER COLUMN public_id UNIQUEIDENTIFIER NOT NULL;
ALTER TABLE commerce_order_item ADD CONSTRAINT UQ_commerce_order_item_public_id UNIQUE (public_id);

ALTER TABLE commerce_order DROP CONSTRAINT CK_commerce_order_lifecycle_time;
ALTER TABLE commerce_order ADD CONSTRAINT CK_commerce_order_lifecycle_time CHECK (
    (status = 'PENDING_PAYMENT' AND paid_at IS NULL AND cancelled_at IS NULL)
    OR (status = 'PAID' AND paid_at IS NOT NULL AND cancelled_at IS NULL)
    OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
);

ALTER TABLE pickup_fulfillment ADD
    prepared_at DATETIME2(6) NULL,
    prepared_by_account_public_id UNIQUEIDENTIFIER NULL,
    handed_over_at DATETIME2(6) NULL,
    handed_over_by_account_public_id UNIQUEIDENTIFIER NULL,
    handover_idempotency_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    cancelled_at DATETIME2(6) NULL,
    cancelled_by_account_public_id UNIQUEIDENTIFIER NULL;

GO

ALTER TABLE pickup_fulfillment ADD
    CONSTRAINT FK_pickup_fulfillment_prepared_by FOREIGN KEY (prepared_by_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT FK_pickup_fulfillment_handed_over_by FOREIGN KEY (handed_over_by_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT FK_pickup_fulfillment_cancelled_by FOREIGN KEY (cancelled_by_account_public_id) REFERENCES iam_user_account(public_id);

ALTER TABLE pickup_fulfillment DROP CONSTRAINT CK_pickup_fulfillment_lifecycle_time;
ALTER TABLE pickup_fulfillment DROP CONSTRAINT CK_pickup_fulfillment_status;

ALTER TABLE pickup_fulfillment ADD CONSTRAINT CK_pickup_fulfillment_status
    CHECK (status IN ('PENDING', 'PICKING', 'PREPARED', 'HANDED_OVER', 'CANCELLED'));

ALTER TABLE pickup_fulfillment ADD CONSTRAINT CK_pickup_fulfillment_lifecycle_time CHECK (
    (status = 'PENDING' AND picking_started_at IS NULL AND prepared_at IS NULL
        AND prepared_by_account_public_id IS NULL AND handed_over_at IS NULL
        AND handed_over_by_account_public_id IS NULL AND handover_idempotency_key IS NULL
        AND cancelled_at IS NULL AND cancelled_by_account_public_id IS NULL)
    OR (status = 'PICKING' AND picking_started_at IS NOT NULL AND prepared_at IS NULL
        AND prepared_by_account_public_id IS NULL AND handed_over_at IS NULL
        AND handed_over_by_account_public_id IS NULL AND handover_idempotency_key IS NULL
        AND cancelled_at IS NULL AND cancelled_by_account_public_id IS NULL)
    OR (status = 'PREPARED' AND picking_started_at IS NOT NULL AND prepared_at IS NOT NULL
        AND prepared_by_account_public_id IS NOT NULL AND handed_over_at IS NULL
        AND handed_over_by_account_public_id IS NULL AND handover_idempotency_key IS NULL
        AND cancelled_at IS NULL AND cancelled_by_account_public_id IS NULL)
    OR (status = 'HANDED_OVER' AND picking_started_at IS NOT NULL AND prepared_at IS NOT NULL
        AND prepared_by_account_public_id IS NOT NULL AND handed_over_at IS NOT NULL
        AND handed_over_by_account_public_id IS NOT NULL AND LEN(handover_idempotency_key) BETWEEN 1 AND 128
        AND cancelled_at IS NULL AND cancelled_by_account_public_id IS NULL)
    OR (status = 'CANCELLED' AND handed_over_at IS NULL AND handed_over_by_account_public_id IS NULL
        AND handover_idempotency_key IS NULL AND cancelled_at IS NOT NULL
        AND cancelled_by_account_public_id IS NOT NULL
        AND ((prepared_at IS NULL AND prepared_by_account_public_id IS NULL)
          OR (picking_started_at IS NOT NULL AND prepared_at IS NOT NULL AND prepared_by_account_public_id IS NOT NULL)))
);

CREATE UNIQUE INDEX UX_pickup_fulfillment_handover_key
    ON pickup_fulfillment(handed_over_by_account_public_id, handover_idempotency_key)
    WHERE handover_idempotency_key IS NOT NULL;

ALTER TABLE inventory_reservation ADD cancelled_restored_at DATETIME2(6) NULL;

GO

ALTER TABLE inventory_reservation DROP CONSTRAINT CK_inventory_reservation_lifecycle_time;
ALTER TABLE inventory_reservation DROP CONSTRAINT CK_inventory_reservation_status;
ALTER TABLE inventory_reservation ALTER COLUMN status VARCHAR(24) NOT NULL;

ALTER TABLE inventory_reservation ADD CONSTRAINT CK_inventory_reservation_status
    CHECK (status IN ('ACTIVE', 'ADOPTED', 'RELEASED', 'CONSUMED', 'EXPIRED', 'COMMITTED', 'CANCELLED_RESTORED'));

ALTER TABLE inventory_reservation ADD CONSTRAINT CK_inventory_reservation_lifecycle_time CHECK (
    (status = 'ACTIVE' AND adopted_at IS NULL AND released_at IS NULL AND consumed_at IS NULL
        AND expired_at IS NULL AND committed_at IS NULL AND cancelled_restored_at IS NULL)
    OR (status = 'ADOPTED' AND adopted_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NULL
        AND expired_at IS NULL AND committed_at IS NULL AND cancelled_restored_at IS NULL)
    OR (status = 'RELEASED' AND released_at IS NOT NULL AND consumed_at IS NULL
        AND expired_at IS NULL AND committed_at IS NULL AND cancelled_restored_at IS NULL)
    OR (status = 'CONSUMED' AND adopted_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NOT NULL
        AND expired_at IS NULL AND cancelled_restored_at IS NULL)
    OR (status = 'EXPIRED' AND expires_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NULL
        AND expired_at IS NOT NULL AND committed_at IS NULL AND cancelled_restored_at IS NULL)
    OR (status = 'COMMITTED' AND adopted_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NULL
        AND expired_at IS NULL AND committed_at IS NOT NULL AND cancelled_restored_at IS NULL)
    OR (status = 'CANCELLED_RESTORED' AND adopted_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NULL
        AND expired_at IS NULL AND committed_at IS NOT NULL AND cancelled_restored_at IS NOT NULL)
);

CREATE TABLE inventory_stock_movement (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_inventory_stock_movement PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_inventory_stock_movement_public_id UNIQUE,
    operation_type VARCHAR(32) NOT NULL,
    operation_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    order_public_id UNIQUEIDENTIFIER NOT NULL,
    reservation_public_id UNIQUEIDENTIFIER NOT NULL,
    variant_public_id UNIQUEIDENTIFIER NOT NULL,
    location_public_id UNIQUEIDENTIFIER NOT NULL,
    actor_account_public_id UNIQUEIDENTIFIER NOT NULL,
    quantity BIGINT NOT NULL,
    on_hand_delta BIGINT NOT NULL,
    reserved_delta BIGINT NOT NULL,
    occurred_at DATETIME2(6) NOT NULL,
    CONSTRAINT FK_inventory_stock_movement_order FOREIGN KEY (order_public_id) REFERENCES commerce_order(public_id),
    CONSTRAINT FK_inventory_stock_movement_reservation FOREIGN KEY (reservation_public_id) REFERENCES inventory_reservation(public_id),
    CONSTRAINT FK_inventory_stock_movement_variant FOREIGN KEY (variant_public_id) REFERENCES catalog_product_variant(public_id),
    CONSTRAINT FK_inventory_stock_movement_location FOREIGN KEY (location_public_id) REFERENCES org_location(public_id),
    CONSTRAINT FK_inventory_stock_movement_actor FOREIGN KEY (actor_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT UQ_inventory_stock_movement_operation UNIQUE (operation_type, order_public_id),
    CONSTRAINT UQ_inventory_stock_movement_key UNIQUE (operation_key),
    CONSTRAINT CK_inventory_stock_movement_type CHECK (operation_type IN ('PICKUP_HANDOVER', 'CANCELLATION_RESTORE')),
    CONSTRAINT CK_inventory_stock_movement_quantity CHECK (quantity > 0),
    CONSTRAINT CK_inventory_stock_movement_delta CHECK (
        (operation_type = 'PICKUP_HANDOVER' AND on_hand_delta = -quantity AND reserved_delta = -quantity)
        OR (operation_type = 'CANCELLATION_RESTORE' AND on_hand_delta = 0 AND reserved_delta = -quantity)
    )
);

CREATE TABLE payment_void_operation (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_payment_void_operation PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_payment_void_operation_public_id UNIQUE,
    payment_id BIGINT NOT NULL CONSTRAINT UQ_payment_void_operation_payment UNIQUE,
    order_public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_payment_void_operation_order UNIQUE,
    actor_account_public_id UNIQUEIDENTIFIER NOT NULL,
    idempotency_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    requested_amount DECIMAL(19,0) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_payment_void_operation_version DEFAULT 0,
    created_at DATETIME2(6) NOT NULL,
    resolved_at DATETIME2(6) NULL,
    CONSTRAINT FK_payment_void_operation_payment FOREIGN KEY (payment_id) REFERENCES payment(id),
    CONSTRAINT FK_payment_void_operation_order FOREIGN KEY (order_public_id) REFERENCES commerce_order(public_id),
    CONSTRAINT FK_payment_void_operation_actor FOREIGN KEY (actor_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT UQ_payment_void_operation_actor_key UNIQUE (actor_account_public_id, idempotency_key),
    CONSTRAINT CK_payment_void_operation_amount CHECK (requested_amount > 0),
    CONSTRAINT CK_payment_void_operation_currency CHECK (currency = 'VND'),
    CONSTRAINT CK_payment_void_operation_status CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'FAILED_RETRYABLE', 'UNKNOWN', 'REVIEW_REQUIRED')),
    CONSTRAINT CK_payment_void_operation_time CHECK (
        (status = 'PROCESSING' AND resolved_at IS NULL)
        OR (status IN ('SUCCEEDED', 'FAILED_RETRYABLE', 'UNKNOWN', 'REVIEW_REQUIRED') AND resolved_at IS NOT NULL)
    )
);

CREATE TABLE payment_void_attempt (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_payment_void_attempt PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_payment_void_attempt_public_id UNIQUE,
    void_operation_id BIGINT NOT NULL,
    generation INT NOT NULL,
    actor_account_public_id UNIQUEIDENTIFIER NOT NULL,
    idempotency_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    merchant_request_reference VARCHAR(32) NOT NULL CONSTRAINT UQ_payment_void_attempt_merchant_reference UNIQUE,
    amount DECIMAL(19,0) NOT NULL,
    status VARCHAR(24) NOT NULL,
    provider_response_id VARCHAR(32) NULL,
    provider_response_code VARCHAR(8) NULL,
    provider_transaction_status VARCHAR(8) NULL,
    provider_transaction_no VARCHAR(32) NULL,
    provider_evidence_hash VARCHAR(64) NULL,
    created_at DATETIME2(6) NOT NULL,
    resolved_at DATETIME2(6) NULL,
    CONSTRAINT FK_payment_void_attempt_operation FOREIGN KEY (void_operation_id) REFERENCES payment_void_operation(id),
    CONSTRAINT FK_payment_void_attempt_actor FOREIGN KEY (actor_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT UQ_payment_void_attempt_generation UNIQUE (void_operation_id, generation),
    CONSTRAINT UQ_payment_void_attempt_key UNIQUE (void_operation_id, idempotency_key),
    CONSTRAINT UQ_payment_void_attempt_actor_key UNIQUE (actor_account_public_id, idempotency_key),
    CONSTRAINT CK_payment_void_attempt_generation CHECK (generation > 0),
    CONSTRAINT CK_payment_void_attempt_amount CHECK (amount > 0),
    CONSTRAINT CK_payment_void_attempt_status CHECK (status IN ('CREATED', 'SUCCEEDED', 'DEFINITIVE_FAILED', 'UNKNOWN', 'REVIEW_REQUIRED')),
    CONSTRAINT CK_payment_void_attempt_time CHECK (
        (status = 'CREATED' AND resolved_at IS NULL)
        OR (status <> 'CREATED' AND resolved_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX UX_payment_void_attempt_provider_transaction
    ON payment_void_attempt(provider_transaction_no)
    WHERE provider_transaction_no IS NOT NULL;

CREATE TABLE payment_void_allocation (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_payment_void_allocation PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_payment_void_allocation_public_id UNIQUE,
    void_operation_id BIGINT NOT NULL,
    void_attempt_id BIGINT NOT NULL,
    component_type VARCHAR(24) NOT NULL,
    component_public_id UNIQUEIDENTIFIER NOT NULL,
    amount DECIMAL(19,0) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    resolved_at DATETIME2(6) NULL,
    CONSTRAINT FK_payment_void_allocation_operation FOREIGN KEY (void_operation_id) REFERENCES payment_void_operation(id),
    CONSTRAINT FK_payment_void_allocation_attempt FOREIGN KEY (void_attempt_id) REFERENCES payment_void_attempt(id),
    CONSTRAINT FK_payment_void_allocation_component FOREIGN KEY (component_public_id) REFERENCES commerce_order_item(public_id),
    CONSTRAINT UQ_payment_void_allocation_component UNIQUE (void_attempt_id, component_type, component_public_id),
    CONSTRAINT CK_payment_void_allocation_component CHECK (component_type = 'ORDER_ITEM'),
    CONSTRAINT CK_payment_void_allocation_amount CHECK (amount > 0),
    CONSTRAINT CK_payment_void_allocation_status CHECK (status IN ('ACTIVE', 'SUCCEEDED', 'RELEASED')),
    CONSTRAINT CK_payment_void_allocation_time CHECK (
        (status = 'ACTIVE' AND resolved_at IS NULL)
        OR (status IN ('SUCCEEDED', 'RELEASED') AND resolved_at IS NOT NULL)
    )
);

CREATE INDEX IX_payment_void_allocation_capacity
    ON payment_void_allocation(component_public_id, status);
