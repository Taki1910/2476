ALTER TABLE commerce_order ADD
    price_quote_public_id UNIQUEIDENTIFIER NULL,
    checkout_idempotency_key NVARCHAR(128) NULL,
    price_version_public_id UNIQUEIDENTIFIER NULL;

GO

ALTER TABLE commerce_order ADD
    CONSTRAINT FK_commerce_order_price_quote FOREIGN KEY (price_quote_public_id) REFERENCES pricing_price_quote(public_id),
    CONSTRAINT FK_commerce_order_price_version FOREIGN KEY (price_version_public_id) REFERENCES pricing_variant_price(public_id),
    CONSTRAINT CK_commerce_order_checkout_evidence CHECK (
        (price_quote_public_id IS NULL AND checkout_idempotency_key IS NULL AND price_version_public_id IS NULL)
        OR (price_quote_public_id IS NOT NULL AND LEN(checkout_idempotency_key) BETWEEN 1 AND 128 AND price_version_public_id IS NOT NULL)
    );

CREATE UNIQUE INDEX UX_commerce_order_price_quote
    ON commerce_order(price_quote_public_id)
    WHERE price_quote_public_id IS NOT NULL;

CREATE UNIQUE INDEX UX_commerce_order_checkout_key
    ON commerce_order(owner_account_public_id, checkout_idempotency_key)
    WHERE checkout_idempotency_key IS NOT NULL;

ALTER TABLE commerce_order_item ADD
    sku_snapshot VARCHAR(64) NULL,
    size_snapshot VARCHAR(32) NULL;

GO

ALTER TABLE commerce_order_item ADD CONSTRAINT CK_commerce_order_item_catalog_snapshot
    CHECK (
        (sku_snapshot IS NULL AND size_snapshot IS NULL)
        OR (LEN(sku_snapshot) BETWEEN 2 AND 64 AND LEN(size_snapshot) BETWEEN 1 AND 32)
    );
