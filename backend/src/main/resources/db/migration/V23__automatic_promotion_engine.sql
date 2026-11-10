CREATE TABLE promotion (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_promotion PRIMARY KEY,
    family_public_id UNIQUEIDENTIFIER NOT NULL,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_promotion_public_id UNIQUE,
    revision_number INT NOT NULL,
    name NVARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL,
    layer VARCHAR(24) NOT NULL,
    effect_type VARCHAR(24) NOT NULL,
    percentage_value DECIMAL(7,4) NULL,
    fixed_amount DECIMAL(19,0) NULL,
    minimum_spend_amount DECIMAL(19,0) NULL,
    buy_quantity BIGINT NULL,
    global_usage_limit BIGINT NULL,
    per_customer_usage_limit BIGINT NULL,
    priority INT NOT NULL,
    valid_from DATETIME2(6) NOT NULL,
    valid_to DATETIME2(6) NULL,
    created_by_account_public_id UNIQUEIDENTIFIER NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    published_at DATETIME2(6) NULL,
    retired_at DATETIME2(6) NULL,
    invalidated_at DATETIME2(6) NULL,
    CONSTRAINT UQ_promotion_family_revision UNIQUE(family_public_id, revision_number),
    CONSTRAINT FK_promotion_creator FOREIGN KEY(created_by_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT CK_promotion_status CHECK(status IN ('DRAFT','PUBLISHED','RETIRED','INVALIDATED')),
    CONSTRAINT CK_promotion_layer CHECK(layer IN ('ITEM','ORDER_AUTOMATIC','SHIPPING')),
    CONSTRAINT CK_promotion_effect CHECK(
      (effect_type='ITEM_PERCENT' AND layer='ITEM' AND percentage_value>0 AND percentage_value<=100 AND fixed_amount IS NULL) OR
      (effect_type='ITEM_FIXED' AND layer='ITEM' AND fixed_amount>0 AND percentage_value IS NULL) OR
      (effect_type='ORDER_PERCENT' AND layer='ORDER_AUTOMATIC' AND percentage_value>0 AND percentage_value<=100 AND fixed_amount IS NULL) OR
      (effect_type='ORDER_FIXED' AND layer='ORDER_AUTOMATIC' AND fixed_amount>0 AND percentage_value IS NULL) OR
      (effect_type='FREE_SHIPPING' AND layer='SHIPPING' AND percentage_value IS NULL AND fixed_amount IS NULL)),
    CONSTRAINT CK_promotion_values CHECK(
      (minimum_spend_amount IS NULL OR minimum_spend_amount>=0) AND
      (buy_quantity IS NULL OR buy_quantity>0) AND
      (global_usage_limit IS NULL OR global_usage_limit>0) AND
      (per_customer_usage_limit IS NULL OR per_customer_usage_limit>0) AND
      (valid_to IS NULL OR valid_to>valid_from)),
    CONSTRAINT CK_promotion_lifecycle CHECK(
      (status='DRAFT' AND published_at IS NULL AND retired_at IS NULL AND invalidated_at IS NULL) OR
      (status='PUBLISHED' AND published_at IS NOT NULL AND retired_at IS NULL AND invalidated_at IS NULL) OR
      (status='RETIRED' AND published_at IS NOT NULL AND retired_at IS NOT NULL AND invalidated_at IS NULL) OR
      (status='INVALIDATED' AND published_at IS NOT NULL AND invalidated_at IS NOT NULL))
);
CREATE INDEX IX_promotion_evaluation ON promotion(status,valid_from,valid_to,layer,priority DESC,public_id);

CREATE TABLE promotion_product_scope (
    promotion_id BIGINT NOT NULL,
    product_public_id UNIQUEIDENTIFIER NOT NULL,
    CONSTRAINT PK_promotion_product_scope PRIMARY KEY(promotion_id,product_public_id),
    CONSTRAINT FK_promotion_scope_promotion FOREIGN KEY(promotion_id) REFERENCES promotion(id),
    CONSTRAINT FK_promotion_scope_product FOREIGN KEY(product_public_id) REFERENCES catalog_product(public_id)
);

ALTER TABLE pricing_cart_quote ADD
    calculation_policy VARCHAR(16) NOT NULL CONSTRAINT DF_pricing_cart_quote_policy DEFAULT 'VND_V1',
    merchandise_discount_amount DECIMAL(19,0) NOT NULL CONSTRAINT DF_pricing_cart_quote_merch_discount DEFAULT 0,
    shipping_discount_amount DECIMAL(19,0) NOT NULL CONSTRAINT DF_pricing_cart_quote_ship_discount DEFAULT 0;
GO
ALTER TABLE pricing_cart_quote DROP CONSTRAINT CK_pricing_cart_quote_money;
ALTER TABLE pricing_cart_quote ADD CONSTRAINT CK_pricing_cart_quote_money CHECK(
    calculation_policy='VND_V1' AND merchandise_amount>0 AND
    merchandise_discount_amount BETWEEN 0 AND merchandise_amount AND
    shipping_fee_amount>=0 AND shipping_discount_amount BETWEEN 0 AND shipping_fee_amount AND
    total_amount=merchandise_amount-merchandise_discount_amount+shipping_fee_amount-shipping_discount_amount AND
    total_amount>0 AND total_amount<=9999999999);

ALTER TABLE commerce_order ADD
    calculation_policy VARCHAR(16) NOT NULL CONSTRAINT DF_commerce_order_policy DEFAULT 'VND_V1',
    merchandise_discount_amount DECIMAL(19,0) NOT NULL CONSTRAINT DF_commerce_order_merch_discount DEFAULT 0,
    shipping_discount_amount DECIMAL(19,0) NOT NULL CONSTRAINT DF_commerce_order_ship_discount DEFAULT 0;
GO
ALTER TABLE commerce_order DROP CONSTRAINT CK_commerce_order_money;
ALTER TABLE commerce_order ADD CONSTRAINT CK_commerce_order_money CHECK(
    calculation_policy='VND_V1' AND merchandise_amount>0 AND
    merchandise_discount_amount BETWEEN 0 AND merchandise_amount AND
    shipping_fee_amount>=0 AND shipping_discount_amount BETWEEN 0 AND shipping_fee_amount AND
    total_amount=merchandise_amount-merchandise_discount_amount+shipping_fee_amount-shipping_discount_amount AND
    total_amount>0 AND total_amount<=9999999999);

CREATE TABLE pricing_cart_quote_adjustment (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_pricing_cart_quote_adjustment PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_pricing_cart_quote_adjustment_public UNIQUE,
    quote_public_id UNIQUEIDENTIFIER NOT NULL,
    variant_public_id UNIQUEIDENTIFIER NULL,
    promotion_family_public_id UNIQUEIDENTIFIER NOT NULL,
    promotion_revision_public_id UNIQUEIDENTIFIER NOT NULL,
    revision_number INT NOT NULL,
    name_snapshot NVARCHAR(120) NOT NULL,
    effect_type VARCHAR(24) NOT NULL,
    layer VARCHAR(24) NOT NULL,
    qualifying_base_amount DECIMAL(19,0) NOT NULL,
    percentage_value DECIMAL(7,4) NULL,
    fixed_amount DECIMAL(19,0) NULL,
    applied_amount DECIMAL(19,0) NOT NULL,
    priority INT NOT NULL,
    qualifying_quantity BIGINT NULL,
    buy_quantity BIGINT NULL,
    applied_at DATETIME2(6) NOT NULL,
    CONSTRAINT FK_quote_adjustment_quote FOREIGN KEY(quote_public_id) REFERENCES pricing_cart_quote(public_id),
    CONSTRAINT FK_quote_adjustment_variant FOREIGN KEY(variant_public_id) REFERENCES catalog_product_variant(public_id),
    CONSTRAINT CK_quote_adjustment_amount CHECK(qualifying_base_amount>=0 AND applied_amount>0 AND applied_amount<=qualifying_base_amount),
    CONSTRAINT CK_quote_adjustment_line CHECK((layer='ITEM' AND variant_public_id IS NOT NULL) OR (layer<>'ITEM' AND variant_public_id IS NULL))
);
CREATE UNIQUE INDEX UX_quote_adjustment_layer ON pricing_cart_quote_adjustment(quote_public_id,layer,variant_public_id);

CREATE TABLE commerce_order_adjustment (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_commerce_order_adjustment PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_commerce_order_adjustment_public UNIQUE,
    order_public_id UNIQUEIDENTIFIER NOT NULL,
    promotion_family_public_id UNIQUEIDENTIFIER NOT NULL,
    promotion_revision_public_id UNIQUEIDENTIFIER NOT NULL,
    revision_number INT NOT NULL,
    name_snapshot NVARCHAR(120) NOT NULL,
    effect_type VARCHAR(24) NOT NULL,
    layer VARCHAR(24) NOT NULL,
    qualifying_base_amount DECIMAL(19,0) NOT NULL,
    percentage_value DECIMAL(7,4) NULL,
    fixed_amount DECIMAL(19,0) NULL,
    applied_amount DECIMAL(19,0) NOT NULL,
    priority INT NOT NULL,
    qualifying_quantity BIGINT NULL,
    buy_quantity BIGINT NULL,
    applied_at DATETIME2(6) NOT NULL,
    CONSTRAINT FK_order_adjustment_order FOREIGN KEY(order_public_id) REFERENCES commerce_order(public_id),
    CONSTRAINT CK_order_adjustment_layer CHECK(layer IN ('ORDER_AUTOMATIC','SHIPPING')),
    CONSTRAINT CK_order_adjustment_amount CHECK(qualifying_base_amount>=0 AND applied_amount>0 AND applied_amount<=qualifying_base_amount),
    CONSTRAINT UQ_order_adjustment_layer UNIQUE(order_public_id,layer)
);

CREATE TABLE commerce_order_item_adjustment (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_commerce_order_item_adjustment PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_commerce_order_item_adjustment_public UNIQUE,
    order_item_public_id UNIQUEIDENTIFIER NOT NULL,
    parent_order_adjustment_public_id UNIQUEIDENTIFIER NULL,
    promotion_family_public_id UNIQUEIDENTIFIER NOT NULL,
    promotion_revision_public_id UNIQUEIDENTIFIER NOT NULL,
    revision_number INT NOT NULL,
    name_snapshot NVARCHAR(120) NOT NULL,
    effect_type VARCHAR(24) NOT NULL,
    layer VARCHAR(24) NOT NULL,
    qualifying_base_amount DECIMAL(19,0) NOT NULL,
    percentage_value DECIMAL(7,4) NULL,
    fixed_amount DECIMAL(19,0) NULL,
    applied_amount DECIMAL(19,0) NOT NULL,
    priority INT NOT NULL,
    qualifying_quantity BIGINT NULL,
    buy_quantity BIGINT NULL,
    applied_at DATETIME2(6) NOT NULL,
    CONSTRAINT FK_item_adjustment_item FOREIGN KEY(order_item_public_id) REFERENCES commerce_order_item(public_id),
    CONSTRAINT FK_item_adjustment_parent FOREIGN KEY(parent_order_adjustment_public_id) REFERENCES commerce_order_adjustment(public_id),
    CONSTRAINT CK_item_adjustment_layer CHECK(layer IN ('ITEM','ORDER_ALLOCATION')),
    CONSTRAINT CK_item_adjustment_amount CHECK(qualifying_base_amount>=0 AND applied_amount>=0 AND applied_amount<=qualifying_base_amount)
);
CREATE UNIQUE INDEX UX_item_adjustment_layer ON commerce_order_item_adjustment(order_item_public_id,layer);

CREATE TABLE promotion_redemption (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_promotion_redemption PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_promotion_redemption_public UNIQUE,
    promotion_revision_public_id UNIQUEIDENTIFIER NOT NULL,
    order_public_id UNIQUEIDENTIFIER NOT NULL,
    customer_account_public_id UNIQUEIDENTIFIER NOT NULL,
    status VARCHAR(16) NOT NULL,
    hold_expires_at DATETIME2(6) NOT NULL,
    reserved_at DATETIME2(6) NOT NULL,
    redeemed_at DATETIME2(6) NULL,
    released_at DATETIME2(6) NULL,
    CONSTRAINT FK_redemption_promotion FOREIGN KEY(promotion_revision_public_id) REFERENCES promotion(public_id),
    CONSTRAINT FK_redemption_order FOREIGN KEY(order_public_id) REFERENCES commerce_order(public_id),
    CONSTRAINT FK_redemption_customer FOREIGN KEY(customer_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT UQ_redemption_promotion_order UNIQUE(promotion_revision_public_id,order_public_id),
    CONSTRAINT CK_redemption_status CHECK(status IN ('RESERVED','REDEEMED','RELEASED')),
    CONSTRAINT CK_redemption_lifecycle CHECK(
      (status='RESERVED' AND redeemed_at IS NULL AND released_at IS NULL) OR
      (status='REDEEMED' AND redeemed_at IS NOT NULL AND released_at IS NULL) OR
      (status='RELEASED' AND redeemed_at IS NULL AND released_at IS NOT NULL))
);
CREATE INDEX IX_redemption_global ON promotion_redemption(promotion_revision_public_id,status);
CREATE INDEX IX_redemption_customer ON promotion_redemption(promotion_revision_public_id,customer_account_public_id,status);

INSERT INTO iam_permission(code) VALUES('PROMOTION_MANAGE');
