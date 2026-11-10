IF EXISTS (
    SELECT family_public_id
    FROM promotion
    WHERE status = 'PUBLISHED'
    GROUP BY family_public_id
    HAVING COUNT_BIG(*) > 1
)
BEGIN
    THROW 51000, 'V24 requires at most one PUBLISHED promotion revision per family.', 1;
END;
GO

CREATE TABLE promotion_family (
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_promotion_family PRIMARY KEY,
    acquisition_mode VARCHAR(16) NOT NULL,
    normalized_code VARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    is_publicly_discoverable BIT NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT CK_promotion_family_acquisition CHECK (
        (acquisition_mode = 'CODE' AND normalized_code IS NOT NULL AND LEN(normalized_code) BETWEEN 3 AND 32
            AND normalized_code = LTRIM(RTRIM(normalized_code))
            AND normalized_code = UPPER(normalized_code)
            AND normalized_code NOT LIKE '%[^A-Z0-9_-]%')
        OR (acquisition_mode IN ('AUTOMATIC','CLAIMABLE') AND normalized_code IS NULL)
    ),
    CONSTRAINT CK_promotion_family_visibility CHECK (
        acquisition_mode <> 'AUTOMATIC' OR is_publicly_discoverable = 0
    )
);

CREATE UNIQUE INDEX UX_promotion_family_normalized_code
    ON promotion_family(normalized_code)
    WHERE normalized_code IS NOT NULL;

INSERT INTO promotion_family(public_id,acquisition_mode,normalized_code,is_publicly_discoverable,created_at)
SELECT family_public_id,'AUTOMATIC',NULL,0,MIN(created_at)
FROM promotion
GROUP BY family_public_id;

ALTER TABLE promotion ADD
    customer_summary NVARCHAR(240) NULL,
    customer_terms NVARCHAR(1000) NULL;
GO

ALTER TABLE promotion ADD CONSTRAINT CK_promotion_customer_summary
    CHECK (customer_summary IS NULL OR LEN(LTRIM(RTRIM(customer_summary))) BETWEEN 1 AND 240);
ALTER TABLE promotion ADD CONSTRAINT CK_promotion_customer_terms
    CHECK (customer_terms IS NULL OR LEN(LTRIM(RTRIM(customer_terms))) BETWEEN 1 AND 1000);
ALTER TABLE promotion ADD CONSTRAINT FK_promotion_family
    FOREIGN KEY(family_public_id) REFERENCES promotion_family(public_id);
ALTER TABLE promotion ADD CONSTRAINT UQ_promotion_public_family
    UNIQUE(public_id,family_public_id);
CREATE UNIQUE INDEX UX_promotion_family_published
    ON promotion(family_public_id)
    WHERE status = 'PUBLISHED';

CREATE TABLE voucher_claim (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_voucher_claim PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_voucher_claim_public UNIQUE,
    promotion_family_public_id UNIQUEIDENTIFIER NOT NULL,
    claimed_revision_public_id UNIQUEIDENTIFIER NOT NULL,
    customer_account_public_id UNIQUEIDENTIFIER NOT NULL,
    status VARCHAR(16) NOT NULL,
    claimed_at DATETIME2(6) NOT NULL,
    revoked_at DATETIME2(6) NULL,
    revoked_by_account_public_id UNIQUEIDENTIFIER NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_voucher_claim_version DEFAULT 0,
    CONSTRAINT UQ_voucher_claim_customer_family UNIQUE(customer_account_public_id,promotion_family_public_id),
    CONSTRAINT FK_voucher_claim_family FOREIGN KEY(promotion_family_public_id) REFERENCES promotion_family(public_id),
    CONSTRAINT FK_voucher_claim_revision_family FOREIGN KEY(claimed_revision_public_id,promotion_family_public_id)
        REFERENCES promotion(public_id,family_public_id),
    CONSTRAINT FK_voucher_claim_customer FOREIGN KEY(customer_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT FK_voucher_claim_revoker FOREIGN KEY(revoked_by_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT CK_voucher_claim_version CHECK(entity_version >= 0),
    CONSTRAINT CK_voucher_claim_lifecycle CHECK (
        (status = 'CLAIMED' AND revoked_at IS NULL AND revoked_by_account_public_id IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_by_account_public_id IS NOT NULL)
    )
);

CREATE INDEX IX_voucher_claim_saved
    ON voucher_claim(customer_account_public_id,status,claimed_at DESC);
CREATE INDEX IX_voucher_claim_family_customer
    ON voucher_claim(promotion_family_public_id,customer_account_public_id);

ALTER TABLE pricing_cart_quote_adjustment ADD
    acquisition_mode VARCHAR(16) NOT NULL CONSTRAINT DF_quote_adjustment_acquisition DEFAULT 'AUTOMATIC',
    masked_code_snapshot NVARCHAR(32) NULL,
    voucher_claim_public_id UNIQUEIDENTIFIER NULL;
GO

ALTER TABLE pricing_cart_quote_adjustment DROP CONSTRAINT CK_quote_adjustment_line;
ALTER TABLE pricing_cart_quote_adjustment ADD CONSTRAINT CK_quote_adjustment_line CHECK (
    (layer = 'ITEM' AND variant_public_id IS NOT NULL)
    OR (layer IN ('ORDER_AUTOMATIC','VOUCHER_SELECTED','SHIPPING') AND variant_public_id IS NULL)
);
ALTER TABLE pricing_cart_quote_adjustment ADD CONSTRAINT CK_quote_adjustment_voucher_snapshot CHECK (
    (acquisition_mode = 'AUTOMATIC' AND masked_code_snapshot IS NULL AND voucher_claim_public_id IS NULL
        AND layer IN ('ITEM','ORDER_AUTOMATIC','SHIPPING'))
    OR (acquisition_mode = 'CODE' AND masked_code_snapshot IS NOT NULL AND voucher_claim_public_id IS NULL
        AND layer IN ('VOUCHER_SELECTED','SHIPPING'))
    OR (acquisition_mode = 'CLAIMABLE' AND masked_code_snapshot IS NULL AND voucher_claim_public_id IS NOT NULL
        AND layer IN ('VOUCHER_SELECTED','SHIPPING'))
);
ALTER TABLE pricing_cart_quote_adjustment ADD CONSTRAINT CK_quote_adjustment_masked_code
    CHECK(masked_code_snapshot IS NULL OR LEN(LTRIM(RTRIM(masked_code_snapshot))) BETWEEN 1 AND 32);

ALTER TABLE commerce_order_adjustment ADD
    acquisition_mode VARCHAR(16) NOT NULL CONSTRAINT DF_order_adjustment_acquisition DEFAULT 'AUTOMATIC',
    masked_code_snapshot NVARCHAR(32) NULL,
    voucher_claim_public_id UNIQUEIDENTIFIER NULL;
GO

ALTER TABLE commerce_order_adjustment DROP CONSTRAINT CK_order_adjustment_layer;
ALTER TABLE commerce_order_adjustment ADD CONSTRAINT CK_order_adjustment_layer
    CHECK(layer IN ('ORDER_AUTOMATIC','VOUCHER_SELECTED','SHIPPING'));
ALTER TABLE commerce_order_adjustment ADD CONSTRAINT CK_order_adjustment_voucher_snapshot CHECK (
    (acquisition_mode = 'AUTOMATIC' AND masked_code_snapshot IS NULL AND voucher_claim_public_id IS NULL
        AND layer IN ('ORDER_AUTOMATIC','SHIPPING'))
    OR (acquisition_mode = 'CODE' AND masked_code_snapshot IS NOT NULL AND voucher_claim_public_id IS NULL
        AND layer IN ('VOUCHER_SELECTED','SHIPPING'))
    OR (acquisition_mode = 'CLAIMABLE' AND masked_code_snapshot IS NULL AND voucher_claim_public_id IS NOT NULL
        AND layer IN ('VOUCHER_SELECTED','SHIPPING'))
);
ALTER TABLE commerce_order_adjustment ADD CONSTRAINT CK_order_adjustment_masked_code
    CHECK(masked_code_snapshot IS NULL OR LEN(LTRIM(RTRIM(masked_code_snapshot))) BETWEEN 1 AND 32);

ALTER TABLE commerce_order_item_adjustment DROP CONSTRAINT CK_item_adjustment_layer;
ALTER TABLE commerce_order_item_adjustment ADD CONSTRAINT CK_item_adjustment_layer CHECK (
    (layer = 'ITEM' AND parent_order_adjustment_public_id IS NULL)
    OR (layer IN ('ORDER_ALLOCATION','VOUCHER_ALLOCATION') AND parent_order_adjustment_public_id IS NOT NULL)
);
