ALTER TABLE pricing_variant_price
    ADD public_id UNIQUEIDENTIFIER NULL,
        valid_from DATETIME2(6) NULL,
        valid_to DATETIME2(6) NULL;
GO

UPDATE pricing_variant_price
SET public_id = NEWID(),
    valid_from = updated_at;
GO

ALTER TABLE pricing_variant_price ALTER COLUMN public_id UNIQUEIDENTIFIER NOT NULL;
ALTER TABLE pricing_variant_price ALTER COLUMN valid_from DATETIME2(6) NOT NULL;
ALTER TABLE pricing_variant_price DROP CONSTRAINT UQ_pricing_variant_price_variant;

ALTER TABLE pricing_variant_price
    ADD CONSTRAINT UQ_pricing_variant_price_public_id UNIQUE (public_id),
        CONSTRAINT CK_pricing_variant_price_window CHECK (valid_to IS NULL OR valid_to > valid_from);

CREATE UNIQUE INDEX UX_pricing_variant_price_current
    ON pricing_variant_price(variant_id)
    WHERE valid_to IS NULL;

CREATE INDEX IX_pricing_variant_price_effective
    ON pricing_variant_price(variant_id, valid_from, valid_to);

CREATE TABLE pricing_price_quote (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_pricing_price_quote PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL,
    owner_account_id BIGINT NOT NULL,
    price_version_id BIGINT NOT NULL,
    amount DECIMAL(19,0) NOT NULL,
    currency CHAR(3) NOT NULL,
    quoted_at DATETIME2(6) NOT NULL,
    expires_at DATETIME2(6) NOT NULL,
    CONSTRAINT UQ_pricing_price_quote_public_id UNIQUE (public_id),
    CONSTRAINT FK_pricing_price_quote_owner FOREIGN KEY (owner_account_id) REFERENCES iam_user_account(id),
    CONSTRAINT FK_pricing_price_quote_version FOREIGN KEY (price_version_id) REFERENCES pricing_variant_price(id),
    CONSTRAINT CK_pricing_price_quote_amount CHECK (amount > 0),
    CONSTRAINT CK_pricing_price_quote_currency CHECK (currency = 'VND'),
    CONSTRAINT CK_pricing_price_quote_expiry CHECK (expires_at > quoted_at)
);

CREATE INDEX IX_pricing_price_quote_owner_time
    ON pricing_price_quote(owner_account_id, quoted_at);

INSERT INTO iam_permission(code) VALUES ('CATALOG_BROWSE');

INSERT INTO iam_role_permission(role_id, permission_id)
SELECT roles.id, permissions.id
FROM iam_role_bundle roles
CROSS JOIN iam_permission permissions
WHERE roles.code = 'CUSTOMER' AND permissions.code = 'CATALOG_BROWSE';
