CREATE TABLE pricing_cart_quote (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_pricing_cart_quote PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_pricing_cart_quote_public_id UNIQUE,
    owner_account_id BIGINT NOT NULL,
    quoted_at DATETIME2(6) NOT NULL,
    expires_at DATETIME2(6) NOT NULL,
    CONSTRAINT FK_pricing_cart_quote_owner FOREIGN KEY (owner_account_id) REFERENCES iam_user_account(id),
    CONSTRAINT CK_pricing_cart_quote_expiry CHECK (expires_at > quoted_at)
);
CREATE INDEX IX_pricing_cart_quote_owner_time ON pricing_cart_quote(owner_account_id, quoted_at);

CREATE TABLE pricing_cart_quote_line (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_pricing_cart_quote_line PRIMARY KEY,
    cart_quote_id BIGINT NOT NULL,
    price_version_id BIGINT NOT NULL,
    variant_public_id UNIQUEIDENTIFIER NOT NULL,
    quantity BIGINT NOT NULL,
    unit_price_amount DECIMAL(19,0) NOT NULL,
    product_name NVARCHAR(200) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    size VARCHAR(32) NOT NULL,
    color VARCHAR(64) NOT NULL,
    CONSTRAINT FK_pricing_cart_quote_line_quote FOREIGN KEY (cart_quote_id) REFERENCES pricing_cart_quote(id),
    CONSTRAINT FK_pricing_cart_quote_line_version FOREIGN KEY (price_version_id) REFERENCES pricing_variant_price(id),
    CONSTRAINT FK_pricing_cart_quote_line_variant FOREIGN KEY (variant_public_id) REFERENCES catalog_product_variant(public_id),
    CONSTRAINT UQ_pricing_cart_quote_line_variant UNIQUE (cart_quote_id, variant_public_id),
    CONSTRAINT CK_pricing_cart_quote_line_quantity CHECK (quantity BETWEEN 1 AND 10),
    CONSTRAINT CK_pricing_cart_quote_line_amount CHECK (unit_price_amount > 0 AND unit_price_amount * quantity <= 9999999999)
);

ALTER TABLE commerce_order ADD cart_quote_public_id UNIQUEIDENTIFIER NULL, checkout_fingerprint VARCHAR(64) NULL;
ALTER TABLE commerce_order_item ADD reservation_public_id UNIQUEIDENTIFIER NULL,
    price_version_public_id UNIQUEIDENTIFIER NULL, color_snapshot VARCHAR(64) NULL;
GO

-- Preserve historical provenance only; never recalculate an old order's money from current Catalog.
UPDATE items SET reservation_public_id = orders.reservation_public_id,
    price_version_public_id = orders.price_version_public_id
FROM commerce_order_item items JOIN commerce_order orders ON orders.id = items.order_id;

ALTER TABLE commerce_order_item DROP CONSTRAINT UQ_commerce_order_item_order;
ALTER TABLE commerce_order_item ADD
    CONSTRAINT UQ_commerce_order_item_variant UNIQUE (order_id, variant_public_id),
    CONSTRAINT FK_commerce_order_item_reservation FOREIGN KEY (reservation_public_id) REFERENCES inventory_reservation(public_id),
    CONSTRAINT FK_commerce_order_item_price_version FOREIGN KEY (price_version_public_id) REFERENCES pricing_variant_price(public_id);
CREATE UNIQUE INDEX UX_commerce_order_item_reservation ON commerce_order_item(reservation_public_id)
    WHERE reservation_public_id IS NOT NULL;
CREATE INDEX IX_commerce_order_item_variant_order ON commerce_order_item(variant_public_id, order_id)
    INCLUDE (reservation_public_id, location_public_id, quantity, unit_price_amount);

ALTER TABLE commerce_order ADD CONSTRAINT FK_commerce_order_cart_quote
    FOREIGN KEY (cart_quote_public_id) REFERENCES pricing_cart_quote(public_id);
CREATE UNIQUE INDEX UX_commerce_order_cart_quote ON commerce_order(cart_quote_public_id)
    WHERE cart_quote_public_id IS NOT NULL;
ALTER TABLE commerce_order DROP CONSTRAINT CK_commerce_order_channel_evidence;
ALTER TABLE commerce_order ADD CONSTRAINT CK_commerce_order_channel_evidence CHECK (
    (channel = 'ONLINE' AND owner_account_public_id IS NOT NULL AND (
        (cart_quote_public_id IS NULL AND checkout_fingerprint IS NULL AND reservation_public_id IS NOT NULL AND (
            (price_quote_public_id IS NULL AND checkout_idempotency_key IS NULL AND price_version_public_id IS NULL)
            OR (price_quote_public_id IS NOT NULL AND checkout_idempotency_key IS NOT NULL
                AND LEN(checkout_idempotency_key) BETWEEN 1 AND 128 AND price_version_public_id IS NOT NULL)))
        OR (cart_quote_public_id IS NOT NULL AND checkout_fingerprint IS NOT NULL AND LEN(checkout_fingerprint) = 64
            AND checkout_idempotency_key IS NOT NULL AND LEN(checkout_idempotency_key) BETWEEN 1 AND 128
            AND reservation_public_id IS NULL AND price_quote_public_id IS NULL AND price_version_public_id IS NULL)))
    OR (channel = 'POS' AND owner_account_public_id IS NULL AND reservation_public_id IS NULL
        AND price_quote_public_id IS NULL AND checkout_idempotency_key IS NULL AND price_version_public_id IS NOT NULL
        AND cart_quote_public_id IS NULL AND checkout_fingerprint IS NULL AND status = 'PAID')
);

DROP INDEX UX_inventory_stock_movement_order_operation ON inventory_stock_movement;
CREATE UNIQUE INDEX UX_inventory_stock_movement_reservation_operation
    ON inventory_stock_movement(operation_type, reservation_public_id) WHERE reservation_public_id IS NOT NULL;
CREATE UNIQUE INDEX UX_inventory_stock_movement_pos_order
    ON inventory_stock_movement(order_public_id) WHERE operation_type = 'POS_CASH_SALE';
