IF NOT EXISTS (SELECT 1 FROM iam_permission WHERE code = 'INVENTORY_TRANSFER')
    INSERT INTO iam_permission(code) VALUES ('INVENTORY_TRANSFER');

IF NOT EXISTS (
    SELECT 1 FROM iam_role_permission role_permissions
    JOIN iam_role_bundle roles ON roles.id = role_permissions.role_id
    JOIN iam_permission permissions ON permissions.id = role_permissions.permission_id
    WHERE roles.code = 'OPERATIONS' AND permissions.code = 'INVENTORY_TRANSFER'
)
    INSERT INTO iam_role_permission(role_id, permission_id)
    SELECT roles.id, permissions.id FROM iam_role_bundle roles CROSS JOIN iam_permission permissions
    WHERE roles.code = 'OPERATIONS' AND permissions.code = 'INVENTORY_TRANSFER';

CREATE TABLE inventory_transfer (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_inventory_transfer PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_inventory_transfer_public_id UNIQUE,
    source_location_public_id UNIQUEIDENTIFIER NOT NULL,
    destination_location_public_id UNIQUEIDENTIFIER NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by_account_public_id UNIQUEIDENTIFIER NOT NULL,
    dispatched_by_account_public_id UNIQUEIDENTIFIER NULL,
    received_by_account_public_id UNIQUEIDENTIFIER NULL,
    created_at DATETIME2(6) NOT NULL,
    dispatched_at DATETIME2(6) NULL,
    received_at DATETIME2(6) NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_inventory_transfer_version DEFAULT 0,
    CONSTRAINT FK_inventory_transfer_source FOREIGN KEY (source_location_public_id) REFERENCES org_location(public_id),
    CONSTRAINT FK_inventory_transfer_destination FOREIGN KEY (destination_location_public_id) REFERENCES org_location(public_id),
    CONSTRAINT FK_inventory_transfer_creator FOREIGN KEY (created_by_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT FK_inventory_transfer_dispatcher FOREIGN KEY (dispatched_by_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT FK_inventory_transfer_receiver FOREIGN KEY (received_by_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT CK_inventory_transfer_locations CHECK (source_location_public_id <> destination_location_public_id),
    CONSTRAINT CK_inventory_transfer_status CHECK (status IN ('DRAFT','DISPATCHED','RECEIVED')),
    CONSTRAINT CK_inventory_transfer_lifecycle CHECK (
        (status = 'DRAFT' AND dispatched_by_account_public_id IS NULL AND received_by_account_public_id IS NULL
            AND dispatched_at IS NULL AND received_at IS NULL)
        OR (status = 'DISPATCHED' AND dispatched_by_account_public_id IS NOT NULL
            AND received_by_account_public_id IS NULL AND dispatched_at IS NOT NULL AND received_at IS NULL
            AND dispatched_at >= created_at)
        OR (status = 'RECEIVED' AND dispatched_by_account_public_id IS NOT NULL
            AND received_by_account_public_id IS NOT NULL AND dispatched_at IS NOT NULL AND received_at IS NOT NULL
            AND dispatched_at >= created_at AND received_at >= dispatched_at)
    )
);

CREATE TABLE inventory_transfer_line (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_inventory_transfer_line PRIMARY KEY,
    transfer_id BIGINT NOT NULL,
    variant_public_id UNIQUEIDENTIFIER NOT NULL,
    requested_quantity BIGINT NOT NULL,
    dispatched_quantity BIGINT NOT NULL CONSTRAINT DF_inventory_transfer_line_dispatched DEFAULT 0,
    received_quantity BIGINT NOT NULL CONSTRAINT DF_inventory_transfer_line_received DEFAULT 0,
    CONSTRAINT FK_inventory_transfer_line_transfer FOREIGN KEY (transfer_id) REFERENCES inventory_transfer(id),
    CONSTRAINT FK_inventory_transfer_line_variant FOREIGN KEY (variant_public_id) REFERENCES catalog_product_variant(public_id),
    CONSTRAINT UQ_inventory_transfer_line_variant UNIQUE (transfer_id, variant_public_id),
    CONSTRAINT CK_inventory_transfer_line_quantities CHECK (
        requested_quantity > 0 AND dispatched_quantity >= 0 AND dispatched_quantity <= requested_quantity
        AND received_quantity >= 0 AND received_quantity <= dispatched_quantity)
);

CREATE TABLE inventory_transfer_command (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_inventory_transfer_command PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_inventory_transfer_command_public_id UNIQUE,
    transfer_public_id UNIQUEIDENTIFIER NOT NULL,
    actor_account_public_id UNIQUEIDENTIFIER NOT NULL,
    idempotency_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    action VARCHAR(16) NOT NULL,
    request_fingerprint NVARCHAR(512) NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT FK_inventory_transfer_command_transfer FOREIGN KEY (transfer_public_id) REFERENCES inventory_transfer(public_id),
    CONSTRAINT FK_inventory_transfer_command_actor FOREIGN KEY (actor_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT UQ_inventory_transfer_command_actor_key UNIQUE (actor_account_public_id, idempotency_key),
    CONSTRAINT CK_inventory_transfer_command_action CHECK (action IN ('CREATE','DISPATCH','RECEIVE')),
    CONSTRAINT CK_inventory_transfer_command_fingerprint CHECK (LEN(request_fingerprint) BETWEEN 1 AND 512)
);

CREATE INDEX IX_inventory_transfer_source_status ON inventory_transfer(source_location_public_id, status, created_at);
CREATE INDEX IX_inventory_transfer_destination_status ON inventory_transfer(destination_location_public_id, status, created_at);
CREATE INDEX IX_inventory_transfer_line_variant ON inventory_transfer_line(variant_public_id, transfer_id);

ALTER TABLE inventory_stock_movement ADD transfer_public_id UNIQUEIDENTIFIER NULL;
GO

ALTER TABLE inventory_stock_movement ADD CONSTRAINT FK_inventory_stock_movement_transfer
    FOREIGN KEY (transfer_public_id) REFERENCES inventory_transfer(public_id);

ALTER TABLE inventory_stock_movement DROP CONSTRAINT CK_inventory_stock_movement_type;
ALTER TABLE inventory_stock_movement DROP CONSTRAINT CK_inventory_stock_movement_quantity;
ALTER TABLE inventory_stock_movement DROP CONSTRAINT CK_inventory_stock_movement_delta;

ALTER TABLE inventory_stock_movement ADD
    CONSTRAINT CK_inventory_stock_movement_type CHECK (operation_type IN (
        'PICKUP_HANDOVER', 'DELIVERY_DISPATCH', 'CANCELLATION_RESTORE', 'POS_CASH_SALE',
        'INVENTORY_ADJUSTMENT', 'RECEIVE', 'TRANSFER_OUT', 'TRANSFER_IN')),
    CONSTRAINT CK_inventory_stock_movement_quantity CHECK (
        (operation_type = 'INVENTORY_ADJUSTMENT' AND quantity >= 0)
        OR (operation_type <> 'INVENTORY_ADJUSTMENT' AND quantity > 0)),
    CONSTRAINT CK_inventory_stock_movement_delta CHECK (
        (transfer_public_id IS NULL AND (
            (operation_type IN ('PICKUP_HANDOVER', 'DELIVERY_DISPATCH') AND order_public_id IS NOT NULL
                AND reservation_public_id IS NOT NULL AND pos_register_public_id IS NULL
                AND cashier_shift_public_id IS NULL AND request_fingerprint IS NULL AND reason IS NULL
                AND before_on_hand IS NULL AND after_on_hand IS NULL AND reserved_at_operation IS NULL
                AND on_hand_delta = -quantity AND reserved_delta = -quantity)
            OR (operation_type = 'CANCELLATION_RESTORE' AND order_public_id IS NOT NULL
                AND reservation_public_id IS NOT NULL AND pos_register_public_id IS NULL
                AND cashier_shift_public_id IS NULL AND request_fingerprint IS NULL AND reason IS NULL
                AND before_on_hand IS NULL AND after_on_hand IS NULL AND reserved_at_operation IS NULL
                AND on_hand_delta = 0 AND reserved_delta = -quantity)
            OR (operation_type = 'POS_CASH_SALE' AND order_public_id IS NOT NULL
                AND reservation_public_id IS NULL AND pos_register_public_id IS NOT NULL
                AND cashier_shift_public_id IS NOT NULL AND request_fingerprint IS NULL AND reason IS NULL
                AND before_on_hand IS NULL AND after_on_hand IS NULL AND reserved_at_operation IS NULL
                AND on_hand_delta = -quantity AND reserved_delta = 0)
            OR (operation_type = 'INVENTORY_ADJUSTMENT' AND order_public_id IS NULL
                AND reservation_public_id IS NULL AND pos_register_public_id IS NULL
                AND cashier_shift_public_id IS NULL AND LEN(request_fingerprint) BETWEEN 1 AND 512
                AND LEN(reason) BETWEEN 1 AND 256 AND before_on_hand >= 0 AND after_on_hand >= 0
                AND reserved_at_operation IS NULL
                AND on_hand_delta = after_on_hand - before_on_hand AND reserved_delta = 0
                AND quantity = ABS(on_hand_delta))
            OR (operation_type = 'RECEIVE' AND order_public_id IS NULL
                AND reservation_public_id IS NULL AND pos_register_public_id IS NULL
                AND cashier_shift_public_id IS NULL AND LEN(request_fingerprint) BETWEEN 1 AND 512
                AND (reason IS NULL OR LEN(reason) BETWEEN 1 AND 256)
                AND before_on_hand >= 0 AND after_on_hand >= before_on_hand
                AND reserved_at_operation >= 0 AND reserved_at_operation <= before_on_hand
                AND on_hand_delta = quantity AND reserved_delta = 0
                AND after_on_hand - before_on_hand = quantity)
        ))
        OR (operation_type = 'TRANSFER_OUT' AND transfer_public_id IS NOT NULL
            AND order_public_id IS NULL AND reservation_public_id IS NULL
            AND pos_register_public_id IS NULL AND cashier_shift_public_id IS NULL
            AND request_fingerprint IS NULL AND reason IS NULL
            AND before_on_hand >= quantity AND after_on_hand = before_on_hand - quantity
            AND reserved_at_operation >= 0 AND after_on_hand >= reserved_at_operation
            AND on_hand_delta = -quantity AND reserved_delta = 0)
        OR (operation_type = 'TRANSFER_IN' AND transfer_public_id IS NOT NULL
            AND order_public_id IS NULL AND reservation_public_id IS NULL
            AND pos_register_public_id IS NULL AND cashier_shift_public_id IS NULL
            AND request_fingerprint IS NULL AND reason IS NULL
            AND before_on_hand >= 0 AND after_on_hand = before_on_hand + quantity
            AND reserved_at_operation >= 0 AND reserved_at_operation <= before_on_hand
            AND on_hand_delta = quantity AND reserved_delta = 0)
    );

CREATE INDEX IX_inventory_stock_movement_transfer ON inventory_stock_movement(transfer_public_id, operation_type);
