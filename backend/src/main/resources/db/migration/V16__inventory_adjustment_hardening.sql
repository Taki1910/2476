ALTER TABLE inventory_stock_movement DROP CONSTRAINT UQ_inventory_stock_movement_operation;
ALTER TABLE inventory_stock_movement ALTER COLUMN order_public_id UNIQUEIDENTIFIER NULL;

ALTER TABLE inventory_stock_movement ADD
    request_fingerprint NVARCHAR(512) NULL,
    reason NVARCHAR(256) NULL,
    before_on_hand BIGINT NULL,
    after_on_hand BIGINT NULL;
GO

CREATE UNIQUE INDEX UX_inventory_stock_movement_order_operation
    ON inventory_stock_movement(operation_type, order_public_id)
    WHERE order_public_id IS NOT NULL;

ALTER TABLE inventory_stock_movement DROP CONSTRAINT CK_inventory_stock_movement_type;
ALTER TABLE inventory_stock_movement DROP CONSTRAINT CK_inventory_stock_movement_quantity;
ALTER TABLE inventory_stock_movement DROP CONSTRAINT CK_inventory_stock_movement_delta;

ALTER TABLE inventory_stock_movement ADD
    CONSTRAINT CK_inventory_stock_movement_type CHECK (operation_type IN (
        'PICKUP_HANDOVER', 'CANCELLATION_RESTORE', 'POS_CASH_SALE', 'INVENTORY_ADJUSTMENT')),
    CONSTRAINT CK_inventory_stock_movement_quantity CHECK (
        (operation_type = 'INVENTORY_ADJUSTMENT' AND quantity >= 0)
        OR (operation_type <> 'INVENTORY_ADJUSTMENT' AND quantity > 0)),
    CONSTRAINT CK_inventory_stock_movement_delta CHECK (
        (operation_type = 'PICKUP_HANDOVER' AND order_public_id IS NOT NULL
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

ALTER TABLE pricing_variant_price ADD CONSTRAINT CK_pricing_variant_price_js_safe
    CHECK (amount <= 9007199254740991);
