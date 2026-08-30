ALTER TABLE commerce_order ADD
    channel VARCHAR(8) NOT NULL CONSTRAINT DF_commerce_order_channel DEFAULT 'ONLINE';

GO

ALTER TABLE commerce_order DROP CONSTRAINT UQ_commerce_order_reservation;
ALTER TABLE commerce_order ALTER COLUMN owner_account_public_id UNIQUEIDENTIFIER NULL;
ALTER TABLE commerce_order ALTER COLUMN reservation_public_id UNIQUEIDENTIFIER NULL;

CREATE UNIQUE INDEX UX_commerce_order_reservation
    ON commerce_order(reservation_public_id)
    WHERE reservation_public_id IS NOT NULL;

ALTER TABLE commerce_order DROP CONSTRAINT CK_commerce_order_checkout_evidence;
ALTER TABLE commerce_order ADD
    CONSTRAINT CK_commerce_order_channel CHECK (channel IN ('ONLINE', 'POS')),
    CONSTRAINT CK_commerce_order_channel_evidence CHECK (
        (channel = 'ONLINE'
            AND owner_account_public_id IS NOT NULL
            AND reservation_public_id IS NOT NULL
            AND (
                (price_quote_public_id IS NULL AND checkout_idempotency_key IS NULL AND price_version_public_id IS NULL)
                OR (price_quote_public_id IS NOT NULL AND LEN(checkout_idempotency_key) BETWEEN 1 AND 128 AND price_version_public_id IS NOT NULL)
            ))
        OR (channel = 'POS'
            AND owner_account_public_id IS NULL
            AND reservation_public_id IS NULL
            AND price_quote_public_id IS NULL
            AND checkout_idempotency_key IS NULL
            AND price_version_public_id IS NOT NULL
            AND status = 'PAID')
    );

CREATE TABLE pos_register (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_pos_register PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_pos_register_public_id UNIQUE,
    code VARCHAR(32) NOT NULL CONSTRAINT UQ_pos_register_code UNIQUE,
    location_id BIGINT NOT NULL,
    enabled BIT NOT NULL CONSTRAINT DF_pos_register_enabled DEFAULT 1,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT UQ_pos_register_id_location UNIQUE (id, location_id),
    CONSTRAINT FK_pos_register_location FOREIGN KEY (location_id) REFERENCES org_location(id),
    CONSTRAINT CK_pos_register_code CHECK (LEN(code) BETWEEN 2 AND 32)
);

GO

CREATE TABLE cashier_shift (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_cashier_shift PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_cashier_shift_public_id UNIQUE,
    register_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    cashier_account_id BIGINT NOT NULL,
    status VARCHAR(8) NOT NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_cashier_shift_version DEFAULT 0,
    opened_at DATETIME2(6) NOT NULL,
    closed_at DATETIME2(6) NULL,
    CONSTRAINT UQ_cashier_shift_id_register UNIQUE (id, register_id),
    CONSTRAINT UQ_cashier_shift_id_cashier UNIQUE (id, cashier_account_id),
    CONSTRAINT FK_cashier_shift_register_location FOREIGN KEY (register_id, location_id)
        REFERENCES pos_register(id, location_id),
    CONSTRAINT FK_cashier_shift_cashier FOREIGN KEY (cashier_account_id) REFERENCES iam_user_account(id),
    CONSTRAINT CK_cashier_shift_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT CK_cashier_shift_time CHECK (
        (status = 'OPEN' AND closed_at IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL AND closed_at >= opened_at)
    )
);

GO

CREATE UNIQUE INDEX UX_cashier_shift_open_register ON cashier_shift(register_id) WHERE status = 'OPEN';
CREATE UNIQUE INDEX UX_cashier_shift_open_cashier ON cashier_shift(cashier_account_id) WHERE status = 'OPEN';

CREATE TABLE pos_cash_sale (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_pos_cash_sale PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_pos_cash_sale_public_id UNIQUE,
    order_id BIGINT NOT NULL CONSTRAINT UQ_pos_cash_sale_order UNIQUE,
    shift_id BIGINT NOT NULL,
    cashier_account_id BIGINT NOT NULL,
    variant_public_id UNIQUEIDENTIFIER NOT NULL,
    idempotency_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT FK_pos_cash_sale_order FOREIGN KEY (order_id) REFERENCES commerce_order(id),
    CONSTRAINT FK_pos_cash_sale_shift_cashier FOREIGN KEY (shift_id, cashier_account_id)
        REFERENCES cashier_shift(id, cashier_account_id),
    CONSTRAINT FK_pos_cash_sale_variant FOREIGN KEY (variant_public_id) REFERENCES catalog_product_variant(public_id),
    CONSTRAINT UQ_pos_cash_sale_shift_key UNIQUE (shift_id, idempotency_key),
    CONSTRAINT CK_pos_cash_sale_key CHECK (LEN(idempotency_key) BETWEEN 1 AND 128)
);

GO

CREATE TABLE cash_tender (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_cash_tender PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_cash_tender_public_id UNIQUE,
    order_id BIGINT NOT NULL CONSTRAINT UQ_cash_tender_order UNIQUE,
    shift_id BIGINT NOT NULL,
    register_id BIGINT NOT NULL,
    cashier_account_id BIGINT NOT NULL,
    amount DECIMAL(19,0) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT FK_cash_tender_order FOREIGN KEY (order_id) REFERENCES commerce_order(id),
    CONSTRAINT FK_cash_tender_shift_register FOREIGN KEY (shift_id, register_id)
        REFERENCES cashier_shift(id, register_id),
    CONSTRAINT FK_cash_tender_shift_cashier FOREIGN KEY (shift_id, cashier_account_id)
        REFERENCES cashier_shift(id, cashier_account_id),
    CONSTRAINT CK_cash_tender_amount CHECK (amount > 0),
    CONSTRAINT CK_cash_tender_currency CHECK (currency = 'VND')
);

GO

ALTER TABLE pickup_fulfillment ADD
    channel VARCHAR(8) NOT NULL CONSTRAINT DF_pickup_fulfillment_channel DEFAULT 'ONLINE';

GO

ALTER TABLE pickup_fulfillment DROP CONSTRAINT CK_pickup_fulfillment_lifecycle_time;
ALTER TABLE pickup_fulfillment ADD
    CONSTRAINT CK_pickup_fulfillment_channel CHECK (channel IN ('ONLINE', 'POS')),
    CONSTRAINT CK_pickup_fulfillment_lifecycle_time CHECK (
        (channel = 'ONLINE' AND (
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
        ))
        OR (channel = 'POS' AND status = 'HANDED_OVER'
            AND picking_started_at IS NULL AND prepared_at IS NULL AND prepared_by_account_public_id IS NULL
            AND handed_over_at IS NOT NULL AND handed_over_by_account_public_id IS NOT NULL
            AND LEN(handover_idempotency_key) BETWEEN 1 AND 128
            AND cancelled_at IS NULL AND cancelled_by_account_public_id IS NULL)
    );

ALTER TABLE inventory_stock_movement ADD
    pos_register_public_id UNIQUEIDENTIFIER NULL,
    cashier_shift_public_id UNIQUEIDENTIFIER NULL;

GO

ALTER TABLE inventory_stock_movement ALTER COLUMN reservation_public_id UNIQUEIDENTIFIER NULL;
ALTER TABLE inventory_stock_movement DROP CONSTRAINT CK_inventory_stock_movement_type;
ALTER TABLE inventory_stock_movement DROP CONSTRAINT CK_inventory_stock_movement_delta;

ALTER TABLE inventory_stock_movement ADD
    CONSTRAINT FK_inventory_stock_movement_register FOREIGN KEY (pos_register_public_id) REFERENCES pos_register(public_id),
    CONSTRAINT FK_inventory_stock_movement_shift FOREIGN KEY (cashier_shift_public_id) REFERENCES cashier_shift(public_id),
    CONSTRAINT CK_inventory_stock_movement_type CHECK (operation_type IN ('PICKUP_HANDOVER', 'CANCELLATION_RESTORE', 'POS_CASH_SALE')),
    CONSTRAINT CK_inventory_stock_movement_delta CHECK (
        (operation_type = 'PICKUP_HANDOVER' AND reservation_public_id IS NOT NULL
            AND pos_register_public_id IS NULL AND cashier_shift_public_id IS NULL
            AND on_hand_delta = -quantity AND reserved_delta = -quantity)
        OR (operation_type = 'CANCELLATION_RESTORE' AND reservation_public_id IS NOT NULL
            AND pos_register_public_id IS NULL AND cashier_shift_public_id IS NULL
            AND on_hand_delta = 0 AND reserved_delta = -quantity)
        OR (operation_type = 'POS_CASH_SALE' AND reservation_public_id IS NULL
            AND pos_register_public_id IS NOT NULL AND cashier_shift_public_id IS NOT NULL
            AND on_hand_delta = -quantity AND reserved_delta = 0)
    );
