CREATE TABLE catalog_product (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_catalog_product PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_catalog_product_public_id UNIQUE,
    name NVARCHAR(160) NOT NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_catalog_product_version DEFAULT 0,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT CK_catalog_product_name CHECK (LEN(LTRIM(RTRIM(name))) > 0)
);

CREATE TABLE catalog_product_variant (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_catalog_product_variant PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_catalog_product_variant_public_id UNIQUE,
    product_id BIGINT NOT NULL,
    sku VARCHAR(64) NOT NULL,
    size VARCHAR(32) NOT NULL,
    color NVARCHAR(64) NOT NULL,
    lifecycle_status VARCHAR(16) NOT NULL CONSTRAINT DF_catalog_variant_status DEFAULT 'DRAFT',
    entity_version BIGINT NOT NULL CONSTRAINT DF_catalog_variant_version DEFAULT 0,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT UQ_catalog_product_variant_sku UNIQUE (sku),
    CONSTRAINT FK_catalog_variant_product FOREIGN KEY (product_id) REFERENCES catalog_product(id),
    CONSTRAINT CK_catalog_variant_sku_trimmed CHECK (sku = LTRIM(RTRIM(sku))),
    CONSTRAINT CK_catalog_variant_size CHECK (LEN(LTRIM(RTRIM(size))) > 0),
    CONSTRAINT CK_catalog_variant_color CHECK (LEN(LTRIM(RTRIM(color))) > 0),
    CONSTRAINT CK_catalog_variant_status CHECK (lifecycle_status IN ('DRAFT', 'PUBLISHED'))
);

CREATE TABLE pricing_variant_price (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_pricing_variant_price PRIMARY KEY,
    variant_id BIGINT NOT NULL CONSTRAINT UQ_pricing_variant_price_variant UNIQUE,
    amount DECIMAL(19,0) NOT NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_pricing_variant_price_version DEFAULT 0,
    updated_at DATETIME2(6) NOT NULL,
    CONSTRAINT FK_pricing_variant_price_variant FOREIGN KEY (variant_id) REFERENCES catalog_product_variant(id),
    CONSTRAINT CK_pricing_variant_price_positive CHECK (amount > 0)
);

CREATE TABLE inventory_balance (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_inventory_balance PRIMARY KEY,
    variant_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    on_hand BIGINT NOT NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_inventory_balance_version DEFAULT 0,
    updated_at DATETIME2(6) NOT NULL,
    CONSTRAINT UQ_inventory_balance_variant_location UNIQUE (variant_id, location_id),
    CONSTRAINT FK_inventory_balance_variant FOREIGN KEY (variant_id) REFERENCES catalog_product_variant(id),
    CONSTRAINT FK_inventory_balance_location FOREIGN KEY (location_id) REFERENCES org_location(id),
    CONSTRAINT CK_inventory_balance_on_hand CHECK (on_hand >= 0)
);
