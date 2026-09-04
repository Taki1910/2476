ALTER TABLE pickup_fulfillment ADD
    fulfillment_type VARCHAR(16) NOT NULL CONSTRAINT DF_pickup_fulfillment_type DEFAULT 'PICKUP',
    receiver_name NVARCHAR(120) NULL,
    receiver_phone VARCHAR(32) NULL,
    delivery_address NVARCHAR(500) NULL,
    delivery_note NVARCHAR(500) NULL,
    delivery_fee_amount DECIMAL(19,0) NOT NULL CONSTRAINT DF_pickup_fulfillment_delivery_fee DEFAULT 0,
    dispatched_at DATETIME2(6) NULL,
    dispatched_by_account_public_id UNIQUEIDENTIFIER NULL,
    dispatch_idempotency_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    delivered_at DATETIME2(6) NULL,
    delivered_by_account_public_id UNIQUEIDENTIFIER NULL,
    delivery_idempotency_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL;

GO

ALTER TABLE pickup_fulfillment ADD
    CONSTRAINT FK_pickup_fulfillment_dispatched_by FOREIGN KEY (dispatched_by_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT FK_pickup_fulfillment_delivered_by FOREIGN KEY (delivered_by_account_public_id) REFERENCES iam_user_account(public_id);

ALTER TABLE pickup_fulfillment DROP CONSTRAINT CK_pickup_fulfillment_lifecycle_time;
ALTER TABLE pickup_fulfillment DROP CONSTRAINT CK_pickup_fulfillment_status;

ALTER TABLE pickup_fulfillment ADD
    CONSTRAINT CK_pickup_fulfillment_status CHECK (status IN (
        'PENDING', 'PICKING', 'PREPARED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'HANDED_OVER', 'CANCELLED')),
    CONSTRAINT CK_pickup_fulfillment_type_snapshot CHECK (
        (fulfillment_type = 'PICKUP' AND receiver_name IS NULL AND receiver_phone IS NULL
            AND delivery_address IS NULL AND delivery_note IS NULL AND delivery_fee_amount = 0
            AND dispatched_at IS NULL AND dispatched_by_account_public_id IS NULL
            AND dispatch_idempotency_key IS NULL AND delivered_at IS NULL
            AND delivered_by_account_public_id IS NULL AND delivery_idempotency_key IS NULL)
        OR (fulfillment_type = 'DELIVERY' AND channel = 'ONLINE'
            AND LEN(receiver_name) BETWEEN 1 AND 120 AND LEN(receiver_phone) BETWEEN 8 AND 32
            AND LEN(delivery_address) BETWEEN 1 AND 500
            AND (delivery_note IS NULL OR LEN(delivery_note) BETWEEN 1 AND 500)
            AND delivery_fee_amount = 0 AND handed_over_at IS NULL
            AND handed_over_by_account_public_id IS NULL AND handover_idempotency_key IS NULL)
    ),
    CONSTRAINT CK_pickup_fulfillment_lifecycle_time CHECK (
        (channel = 'POS' AND fulfillment_type = 'PICKUP' AND status = 'HANDED_OVER'
            AND picking_started_at IS NULL AND prepared_at IS NULL AND prepared_by_account_public_id IS NULL
            AND handed_over_at IS NOT NULL AND handed_over_by_account_public_id IS NOT NULL
            AND LEN(handover_idempotency_key) BETWEEN 1 AND 128
            AND cancelled_at IS NULL AND cancelled_by_account_public_id IS NULL)
        OR (channel = 'ONLINE' AND (
            (status = 'PENDING' AND picking_started_at IS NULL AND prepared_at IS NULL
                AND prepared_by_account_public_id IS NULL AND handed_over_at IS NULL
                AND handed_over_by_account_public_id IS NULL AND handover_idempotency_key IS NULL
                AND dispatched_at IS NULL AND dispatched_by_account_public_id IS NULL
                AND dispatch_idempotency_key IS NULL AND delivered_at IS NULL
                AND delivered_by_account_public_id IS NULL AND delivery_idempotency_key IS NULL
                AND cancelled_at IS NULL AND cancelled_by_account_public_id IS NULL)
            OR (status = 'PICKING' AND picking_started_at IS NOT NULL AND prepared_at IS NULL
                AND prepared_by_account_public_id IS NULL AND handed_over_at IS NULL
                AND dispatched_at IS NULL AND delivered_at IS NULL
                AND cancelled_at IS NULL AND cancelled_by_account_public_id IS NULL)
            OR (status = 'PREPARED' AND picking_started_at IS NOT NULL AND prepared_at IS NOT NULL
                AND prepared_by_account_public_id IS NOT NULL AND handed_over_at IS NULL
                AND dispatched_at IS NULL AND delivered_at IS NULL
                AND cancelled_at IS NULL AND cancelled_by_account_public_id IS NULL)
            OR (fulfillment_type = 'PICKUP' AND status = 'HANDED_OVER'
                AND picking_started_at IS NOT NULL AND prepared_at IS NOT NULL
                AND prepared_by_account_public_id IS NOT NULL AND handed_over_at IS NOT NULL
                AND handed_over_by_account_public_id IS NOT NULL AND LEN(handover_idempotency_key) BETWEEN 1 AND 128
                AND cancelled_at IS NULL AND cancelled_by_account_public_id IS NULL)
            OR (fulfillment_type = 'DELIVERY' AND status = 'OUT_FOR_DELIVERY'
                AND picking_started_at IS NOT NULL AND prepared_at IS NOT NULL
                AND prepared_by_account_public_id IS NOT NULL AND dispatched_at IS NOT NULL
                AND dispatched_by_account_public_id IS NOT NULL AND LEN(dispatch_idempotency_key) BETWEEN 1 AND 128
                AND delivered_at IS NULL AND delivered_by_account_public_id IS NULL
                AND delivery_idempotency_key IS NULL AND cancelled_at IS NULL
                AND cancelled_by_account_public_id IS NULL)
            OR (fulfillment_type = 'DELIVERY' AND status = 'DELIVERED'
                AND picking_started_at IS NOT NULL AND prepared_at IS NOT NULL
                AND prepared_by_account_public_id IS NOT NULL AND dispatched_at IS NOT NULL
                AND dispatched_by_account_public_id IS NOT NULL AND LEN(dispatch_idempotency_key) BETWEEN 1 AND 128
                AND delivered_at IS NOT NULL AND delivered_by_account_public_id IS NOT NULL
                AND LEN(delivery_idempotency_key) BETWEEN 1 AND 128
                AND cancelled_at IS NULL AND cancelled_by_account_public_id IS NULL)
            OR (status = 'CANCELLED' AND handed_over_at IS NULL AND dispatched_at IS NULL
                AND delivered_at IS NULL AND cancelled_at IS NOT NULL
                AND cancelled_by_account_public_id IS NOT NULL
                AND ((picking_started_at IS NULL AND prepared_at IS NULL AND prepared_by_account_public_id IS NULL)
                  OR (picking_started_at IS NOT NULL AND prepared_at IS NULL AND prepared_by_account_public_id IS NULL)
                  OR (picking_started_at IS NOT NULL AND prepared_at IS NOT NULL AND prepared_by_account_public_id IS NOT NULL)))
        ))
    );

CREATE UNIQUE INDEX UX_pickup_fulfillment_dispatch_key
    ON pickup_fulfillment(dispatched_by_account_public_id, dispatch_idempotency_key)
    WHERE dispatch_idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX UX_pickup_fulfillment_delivery_key
    ON pickup_fulfillment(delivered_by_account_public_id, delivery_idempotency_key)
    WHERE delivery_idempotency_key IS NOT NULL;

ALTER TABLE inventory_stock_movement DROP CONSTRAINT CK_inventory_stock_movement_type;
ALTER TABLE inventory_stock_movement DROP CONSTRAINT CK_inventory_stock_movement_quantity;
ALTER TABLE inventory_stock_movement DROP CONSTRAINT CK_inventory_stock_movement_delta;

ALTER TABLE inventory_stock_movement ADD
    CONSTRAINT CK_inventory_stock_movement_type CHECK (operation_type IN (
        'PICKUP_HANDOVER', 'DELIVERY_DISPATCH', 'CANCELLATION_RESTORE', 'POS_CASH_SALE', 'INVENTORY_ADJUSTMENT')),
    CONSTRAINT CK_inventory_stock_movement_quantity CHECK (
        (operation_type = 'INVENTORY_ADJUSTMENT' AND quantity >= 0)
        OR (operation_type <> 'INVENTORY_ADJUSTMENT' AND quantity > 0)),
    CONSTRAINT CK_inventory_stock_movement_delta CHECK (
        (operation_type IN ('PICKUP_HANDOVER', 'DELIVERY_DISPATCH') AND order_public_id IS NOT NULL
            AND reservation_public_id IS NOT NULL AND pos_register_public_id IS NULL
            AND cashier_shift_public_id IS NULL AND request_fingerprint IS NULL AND reason IS NULL
            AND before_on_hand IS NULL AND after_on_hand IS NULL
            AND on_hand_delta = -quantity AND reserved_delta = -quantity)
        OR (operation_type = 'CANCELLATION_RESTORE' AND order_public_id IS NOT NULL
            AND reservation_public_id IS NOT NULL AND pos_register_public_id IS NULL
            AND cashier_shift_public_id IS NULL AND request_fingerprint IS NULL AND reason IS NULL
            AND before_on_hand IS NULL AND after_on_hand IS NULL
            AND on_hand_delta = 0 AND reserved_delta = -quantity)
        OR (operation_type = 'POS_CASH_SALE' AND order_public_id IS NOT NULL
            AND reservation_public_id IS NULL AND pos_register_public_id IS NOT NULL
            AND cashier_shift_public_id IS NOT NULL AND request_fingerprint IS NULL AND reason IS NULL
            AND before_on_hand IS NULL AND after_on_hand IS NULL
            AND on_hand_delta = -quantity AND reserved_delta = 0)
        OR (operation_type = 'INVENTORY_ADJUSTMENT' AND order_public_id IS NULL
            AND reservation_public_id IS NULL AND pos_register_public_id IS NULL
            AND cashier_shift_public_id IS NULL AND LEN(request_fingerprint) BETWEEN 1 AND 512
            AND LEN(reason) BETWEEN 1 AND 256 AND before_on_hand >= 0 AND after_on_hand >= 0
            AND on_hand_delta = after_on_hand - before_on_hand AND reserved_delta = 0
            AND quantity = ABS(on_hand_delta))
    );

INSERT INTO iam_permission(code) VALUES ('FULFILL_ORDER');
INSERT INTO iam_role_permission(role_id, permission_id)
SELECT roles.id, permissions.id
FROM iam_role_bundle roles CROSS JOIN iam_permission permissions
WHERE roles.code = 'OPERATIONS' AND permissions.code = 'FULFILL_ORDER';
