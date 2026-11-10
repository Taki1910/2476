CREATE TABLE geo_province (
    code VARCHAR(12) NOT NULL CONSTRAINT PK_geo_province PRIMARY KEY,
    display_label NVARCHAR(120) NOT NULL,
    enabled BIT NOT NULL CONSTRAINT DF_geo_province_enabled DEFAULT 1,
    dataset_version VARCHAR(32) NOT NULL,
    CONSTRAINT CK_geo_province_label CHECK (LEN(display_label) BETWEEN 1 AND 120)
);

CREATE TABLE geo_district (
    code VARCHAR(12) NOT NULL CONSTRAINT PK_geo_district PRIMARY KEY,
    province_code VARCHAR(12) NOT NULL,
    display_label NVARCHAR(120) NOT NULL,
    enabled BIT NOT NULL CONSTRAINT DF_geo_district_enabled DEFAULT 1,
    dataset_version VARCHAR(32) NOT NULL,
    CONSTRAINT FK_geo_district_province FOREIGN KEY (province_code) REFERENCES geo_province(code),
    CONSTRAINT UQ_geo_district_province_code UNIQUE (province_code, code),
    CONSTRAINT CK_geo_district_label CHECK (LEN(display_label) BETWEEN 1 AND 120)
);
CREATE INDEX IX_geo_district_province_enabled ON geo_district(province_code, enabled, display_label);

-- GSO administrative-code snapshot used by the project before the 2025 reorganisation.
INSERT INTO geo_province(code, display_label, dataset_version) VALUES
    ('01', N'Thành phố Hà Nội', 'GSO-2024'),
    ('79', N'Thành phố Hồ Chí Minh', 'GSO-2024'),
    ('48', N'Thành phố Đà Nẵng', 'GSO-2024');
INSERT INTO geo_district(code, province_code, display_label, dataset_version) VALUES
    ('001', '01', N'Quận Ba Đình', 'GSO-2024'),
    ('005', '01', N'Quận Cầu Giấy', 'GSO-2024'),
    ('017', '01', N'Huyện Đông Anh', 'GSO-2024'),
    ('760', '79', N'Quận 1', 'GSO-2024'),
    ('769', '79', N'Thành phố Thủ Đức', 'GSO-2024'),
    ('492', '48', N'Quận Hải Châu', 'GSO-2024');

CREATE TABLE shipping_rate_rule (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_shipping_rate_rule PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_shipping_rate_rule_public_id UNIQUE,
    family_public_id UNIQUEIDENTIFIER NOT NULL,
    revision_number INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    origin_scope VARCHAR(16) NOT NULL,
    origin_location_public_id UNIQUEIDENTIFIER NULL,
    origin_branch_public_id UNIQUEIDENTIFIER NULL,
    destination_province_code VARCHAR(12) NOT NULL,
    destination_district_code VARCHAR(12) NULL,
    zone_code VARCHAR(32) NOT NULL,
    fee_amount DECIMAL(19,0) NOT NULL,
    priority INT NOT NULL,
    valid_from DATETIME2(6) NOT NULL,
    valid_to DATETIME2(6) NULL,
    created_by_account_public_id UNIQUEIDENTIFIER NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    published_at DATETIME2(6) NULL,
    retired_at DATETIME2(6) NULL,
    invalidated_at DATETIME2(6) NULL,
    CONSTRAINT UQ_shipping_rate_rule_family_revision UNIQUE (family_public_id, revision_number),
    CONSTRAINT FK_shipping_rate_rule_location FOREIGN KEY (origin_location_public_id) REFERENCES org_location(public_id),
    CONSTRAINT FK_shipping_rate_rule_branch FOREIGN KEY (origin_branch_public_id) REFERENCES org_branch(public_id),
    CONSTRAINT FK_shipping_rate_rule_province FOREIGN KEY (destination_province_code) REFERENCES geo_province(code),
    CONSTRAINT FK_shipping_rate_rule_district FOREIGN KEY (destination_province_code, destination_district_code)
        REFERENCES geo_district(province_code, code),
    CONSTRAINT FK_shipping_rate_rule_creator FOREIGN KEY (created_by_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT CK_shipping_rate_rule_revision CHECK (revision_number > 0),
    CONSTRAINT CK_shipping_rate_rule_status CHECK (status IN ('DRAFT','PUBLISHED','RETIRED','INVALIDATED')),
    CONSTRAINT CK_shipping_rate_rule_origin CHECK (
        (origin_scope = 'LOCATION' AND origin_location_public_id IS NOT NULL AND origin_branch_public_id IS NULL) OR
        (origin_scope = 'BRANCH' AND origin_location_public_id IS NULL AND origin_branch_public_id IS NOT NULL) OR
        (origin_scope = 'GLOBAL' AND origin_location_public_id IS NULL AND origin_branch_public_id IS NULL)),
    CONSTRAINT CK_shipping_rate_rule_zone CHECK (zone_code IN ('LOCAL_CORE','LOCAL_OUTER','INTER_PROVINCE')),
    CONSTRAINT CK_shipping_rate_rule_fee CHECK (fee_amount >= 0 AND fee_amount <= 9999999999),
    CONSTRAINT CK_shipping_rate_rule_priority CHECK (priority BETWEEN -100000 AND 100000),
    CONSTRAINT CK_shipping_rate_rule_time CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT CK_shipping_rate_rule_lifecycle CHECK (
        (status='DRAFT' AND published_at IS NULL AND retired_at IS NULL AND invalidated_at IS NULL) OR
        (status='PUBLISHED' AND published_at IS NOT NULL AND retired_at IS NULL AND invalidated_at IS NULL) OR
        (status='RETIRED' AND published_at IS NOT NULL AND retired_at IS NOT NULL AND invalidated_at IS NULL) OR
        (status='INVALIDATED' AND published_at IS NOT NULL AND invalidated_at IS NOT NULL))
);
CREATE INDEX IX_shipping_rate_rule_conflict ON shipping_rate_rule(
    origin_scope, origin_location_public_id, origin_branch_public_id,
    destination_province_code, destination_district_code, priority, valid_from, valid_to, status);
CREATE INDEX IX_shipping_rate_rule_quote ON shipping_rate_rule(status, destination_province_code,
    destination_district_code, valid_from, valid_to) INCLUDE(origin_scope, origin_location_public_id,
    origin_branch_public_id, priority, fee_amount, zone_code);

ALTER TABLE pricing_cart_quote ADD
    fulfillment_type VARCHAR(16) NOT NULL CONSTRAINT DF_pricing_cart_quote_fulfillment DEFAULT 'PICKUP',
    origin_location_public_id UNIQUEIDENTIFIER NULL,
    origin_branch_public_id UNIQUEIDENTIFIER NULL,
    destination_province_code VARCHAR(12) NULL,
    destination_district_code VARCHAR(12) NULL,
    shipping_rule_public_id UNIQUEIDENTIFIER NULL,
    shipping_zone_code VARCHAR(32) NULL,
    merchandise_amount DECIMAL(19,0) NULL,
    shipping_fee_amount DECIMAL(19,0) NOT NULL CONSTRAINT DF_pricing_cart_quote_shipping DEFAULT 0,
    total_amount DECIMAL(19,0) NULL;
GO
UPDATE quotes SET merchandise_amount = totals.amount, total_amount = totals.amount
FROM pricing_cart_quote quotes
JOIN (SELECT cart_quote_id, SUM(unit_price_amount * quantity) amount FROM pricing_cart_quote_line GROUP BY cart_quote_id) totals
  ON totals.cart_quote_id = quotes.id;
ALTER TABLE pricing_cart_quote ALTER COLUMN merchandise_amount DECIMAL(19,0) NOT NULL;
ALTER TABLE pricing_cart_quote ALTER COLUMN total_amount DECIMAL(19,0) NOT NULL;
ALTER TABLE pricing_cart_quote ADD
    CONSTRAINT FK_pricing_cart_quote_origin_location FOREIGN KEY (origin_location_public_id) REFERENCES org_location(public_id),
    CONSTRAINT FK_pricing_cart_quote_origin_branch FOREIGN KEY (origin_branch_public_id) REFERENCES org_branch(public_id),
    CONSTRAINT FK_pricing_cart_quote_destination_province FOREIGN KEY (destination_province_code) REFERENCES geo_province(code),
    CONSTRAINT FK_pricing_cart_quote_destination_district FOREIGN KEY (destination_province_code, destination_district_code) REFERENCES geo_district(province_code, code),
    CONSTRAINT FK_pricing_cart_quote_shipping_rule FOREIGN KEY (shipping_rule_public_id) REFERENCES shipping_rate_rule(public_id),
    CONSTRAINT CK_pricing_cart_quote_money CHECK (merchandise_amount > 0 AND shipping_fee_amount >= 0 AND total_amount = merchandise_amount + shipping_fee_amount AND total_amount <= 9999999999),
    CONSTRAINT CK_pricing_cart_quote_shipping CHECK (
      (fulfillment_type='PICKUP' AND origin_location_public_id IS NULL AND origin_branch_public_id IS NULL AND destination_province_code IS NULL AND destination_district_code IS NULL AND shipping_rule_public_id IS NULL AND shipping_zone_code IS NULL AND shipping_fee_amount=0)
      OR
      (fulfillment_type='DELIVERY' AND origin_location_public_id IS NOT NULL AND origin_branch_public_id IS NOT NULL AND destination_province_code IS NOT NULL AND destination_district_code IS NOT NULL AND shipping_rule_public_id IS NOT NULL AND shipping_zone_code IS NOT NULL));

ALTER TABLE commerce_order ADD
    merchandise_amount DECIMAL(19,0) NULL,
    shipping_fee_amount DECIMAL(19,0) NOT NULL CONSTRAINT DF_commerce_order_shipping DEFAULT 0,
    total_amount DECIMAL(19,0) NULL,
    shipping_rule_public_id UNIQUEIDENTIFIER NULL,
    shipping_zone_code VARCHAR(32) NULL,
    shipping_origin_location_public_id UNIQUEIDENTIFIER NULL,
    shipping_origin_branch_public_id UNIQUEIDENTIFIER NULL,
    destination_province_code VARCHAR(12) NULL,
    destination_district_code VARCHAR(12) NULL,
    shipping_quoted_at DATETIME2(6) NULL;
GO
UPDATE orders SET merchandise_amount = totals.amount, total_amount = totals.amount
FROM commerce_order orders
JOIN (SELECT order_id, SUM(unit_price_amount * quantity) amount FROM commerce_order_item GROUP BY order_id) totals
  ON totals.order_id = orders.id;
ALTER TABLE commerce_order ALTER COLUMN merchandise_amount DECIMAL(19,0) NOT NULL;
ALTER TABLE commerce_order ALTER COLUMN total_amount DECIMAL(19,0) NOT NULL;
ALTER TABLE commerce_order ADD
    CONSTRAINT FK_commerce_order_shipping_rule FOREIGN KEY (shipping_rule_public_id) REFERENCES shipping_rate_rule(public_id),
    CONSTRAINT FK_commerce_order_shipping_location FOREIGN KEY (shipping_origin_location_public_id) REFERENCES org_location(public_id),
    CONSTRAINT FK_commerce_order_shipping_branch FOREIGN KEY (shipping_origin_branch_public_id) REFERENCES org_branch(public_id),
    CONSTRAINT FK_commerce_order_destination_province FOREIGN KEY (destination_province_code) REFERENCES geo_province(code),
    CONSTRAINT FK_commerce_order_destination_district FOREIGN KEY (destination_province_code, destination_district_code) REFERENCES geo_district(province_code, code),
    CONSTRAINT CK_commerce_order_money CHECK (merchandise_amount > 0 AND shipping_fee_amount >= 0 AND total_amount = merchandise_amount + shipping_fee_amount AND total_amount <= 9999999999),
    CONSTRAINT CK_commerce_order_shipping_evidence CHECK (
      (shipping_rule_public_id IS NULL AND shipping_zone_code IS NULL AND shipping_origin_location_public_id IS NULL AND shipping_origin_branch_public_id IS NULL AND destination_province_code IS NULL AND destination_district_code IS NULL AND shipping_quoted_at IS NULL AND shipping_fee_amount=0)
      OR
      (channel='ONLINE' AND cart_quote_public_id IS NOT NULL AND shipping_rule_public_id IS NOT NULL AND shipping_zone_code IS NOT NULL AND shipping_origin_location_public_id IS NOT NULL AND shipping_origin_branch_public_id IS NOT NULL AND destination_province_code IS NOT NULL AND destination_district_code IS NOT NULL AND shipping_quoted_at IS NOT NULL));

ALTER TABLE pickup_fulfillment DROP CONSTRAINT CK_pickup_fulfillment_type_snapshot;
ALTER TABLE pickup_fulfillment DROP CONSTRAINT DF_pickup_fulfillment_delivery_fee;
ALTER TABLE pickup_fulfillment DROP COLUMN delivery_fee_amount;
ALTER TABLE pickup_fulfillment ADD CONSTRAINT CK_pickup_fulfillment_type_snapshot CHECK (
    (fulfillment_type = 'PICKUP' AND receiver_name IS NULL AND receiver_phone IS NULL
        AND delivery_address IS NULL AND delivery_note IS NULL
        AND dispatched_at IS NULL AND dispatched_by_account_public_id IS NULL
        AND dispatch_idempotency_key IS NULL AND delivered_at IS NULL
        AND delivered_by_account_public_id IS NULL AND delivery_idempotency_key IS NULL)
    OR (fulfillment_type = 'DELIVERY' AND channel = 'ONLINE'
        AND LEN(receiver_name) BETWEEN 1 AND 120 AND LEN(receiver_phone) BETWEEN 8 AND 32
        AND LEN(delivery_address) BETWEEN 1 AND 500
        AND (delivery_note IS NULL OR LEN(delivery_note) BETWEEN 1 AND 500)
        AND handed_over_at IS NULL AND handed_over_by_account_public_id IS NULL AND handover_idempotency_key IS NULL));

INSERT INTO iam_permission(code) VALUES ('SHIPPING_RATE_MANAGE');
