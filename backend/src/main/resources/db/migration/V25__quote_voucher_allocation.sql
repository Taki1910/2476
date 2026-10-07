CREATE TABLE pricing_cart_quote_item_adjustment (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_pricing_cart_quote_item_adjustment PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_pricing_cart_quote_item_adjustment_public UNIQUE,
    quote_public_id UNIQUEIDENTIFIER NOT NULL,
    variant_public_id UNIQUEIDENTIFIER NOT NULL,
    parent_quote_adjustment_public_id UNIQUEIDENTIFIER NOT NULL,
    promotion_family_public_id UNIQUEIDENTIFIER NOT NULL,
    promotion_revision_public_id UNIQUEIDENTIFIER NOT NULL,
    layer VARCHAR(24) NOT NULL,
    applied_amount DECIMAL(19,0) NOT NULL,
    CONSTRAINT FK_quote_item_adjustment_quote FOREIGN KEY(quote_public_id) REFERENCES pricing_cart_quote(public_id),
    CONSTRAINT FK_quote_item_adjustment_variant FOREIGN KEY(variant_public_id) REFERENCES catalog_product_variant(public_id),
    CONSTRAINT FK_quote_item_adjustment_parent FOREIGN KEY(parent_quote_adjustment_public_id) REFERENCES pricing_cart_quote_adjustment(public_id),
    CONSTRAINT CK_quote_item_adjustment_layer CHECK(layer IN ('ORDER_ALLOCATION','VOUCHER_ALLOCATION')),
    CONSTRAINT CK_quote_item_adjustment_amount CHECK(applied_amount >= 0),
    CONSTRAINT UQ_quote_item_adjustment_layer UNIQUE(quote_public_id,variant_public_id,layer)
);
