ALTER TABLE inventory_balance ADD
    reserved BIGINT NOT NULL CONSTRAINT DF_inventory_balance_reserved DEFAULT 0;

GO

ALTER TABLE inventory_balance ADD CONSTRAINT CK_inventory_balance_reserved
    CHECK (reserved >= 0 AND reserved <= on_hand);

CREATE TABLE inventory_reservation (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_inventory_reservation PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_inventory_reservation_public_id UNIQUE,
    owner_account_public_id UNIQUEIDENTIFIER NOT NULL,
    variant_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_inventory_reservation_version DEFAULT 0,
    created_at DATETIME2(6) NOT NULL,
    released_at DATETIME2(6) NULL,
    CONSTRAINT FK_inventory_reservation_owner FOREIGN KEY (owner_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT FK_inventory_reservation_variant FOREIGN KEY (variant_id) REFERENCES catalog_product_variant(id),
    CONSTRAINT FK_inventory_reservation_location FOREIGN KEY (location_id) REFERENCES org_location(id),
    CONSTRAINT CK_inventory_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT CK_inventory_reservation_status CHECK (status IN ('ACTIVE', 'RELEASED')),
    CONSTRAINT CK_inventory_reservation_release_time CHECK ((status = 'ACTIVE' AND released_at IS NULL) OR (status = 'RELEASED' AND released_at IS NOT NULL))
);

GO

CREATE INDEX IX_inventory_reservation_balance_status
    ON inventory_reservation(variant_id, location_id, status);

INSERT INTO iam_permission(code) VALUES ('CHECKOUT_RESERVE');

INSERT INTO iam_role_permission(role_id, permission_id)
SELECT roles.id, permissions.id
FROM iam_role_bundle roles
CROSS JOIN iam_permission permissions
WHERE roles.code = 'CUSTOMER' AND permissions.code = 'CHECKOUT_RESERVE';
