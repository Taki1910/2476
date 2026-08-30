ALTER TABLE inventory_reservation ADD adopted_at DATETIME2(6) NULL;

GO

ALTER TABLE inventory_reservation DROP CONSTRAINT CK_inventory_reservation_release_time;
ALTER TABLE inventory_reservation DROP CONSTRAINT CK_inventory_reservation_status;

ALTER TABLE inventory_reservation ADD CONSTRAINT CK_inventory_reservation_status
    CHECK (status IN ('ACTIVE', 'ADOPTED', 'RELEASED'));

ALTER TABLE inventory_reservation ADD CONSTRAINT CK_inventory_reservation_lifecycle_time
    CHECK (
        (status = 'ACTIVE' AND adopted_at IS NULL AND released_at IS NULL)
        OR (status = 'ADOPTED' AND adopted_at IS NOT NULL AND released_at IS NULL)
        OR (status = 'RELEASED' AND released_at IS NOT NULL)
    );

CREATE TABLE commerce_order (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_commerce_order PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_commerce_order_public_id UNIQUE,
    owner_account_public_id UNIQUEIDENTIFIER NOT NULL,
    responsible_branch_public_id UNIQUEIDENTIFIER NOT NULL,
    reservation_public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_commerce_order_reservation UNIQUE,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_commerce_order_version DEFAULT 0,
    created_at DATETIME2(6) NOT NULL,
    cancelled_at DATETIME2(6) NULL,
    CONSTRAINT FK_commerce_order_owner FOREIGN KEY (owner_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT FK_commerce_order_branch FOREIGN KEY (responsible_branch_public_id) REFERENCES org_branch(public_id),
    CONSTRAINT FK_commerce_order_reservation FOREIGN KEY (reservation_public_id) REFERENCES inventory_reservation(public_id),
    CONSTRAINT CK_commerce_order_currency CHECK (currency = 'VND'),
    CONSTRAINT CK_commerce_order_status CHECK (status IN ('PENDING_PAYMENT', 'CANCELLED')),
    CONSTRAINT CK_commerce_order_cancelled_time CHECK ((status = 'PENDING_PAYMENT' AND cancelled_at IS NULL) OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL))
);

GO

CREATE TABLE commerce_order_item (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_commerce_order_item PRIMARY KEY,
    order_id BIGINT NOT NULL CONSTRAINT UQ_commerce_order_item_order UNIQUE,
    variant_public_id UNIQUEIDENTIFIER NOT NULL,
    location_public_id UNIQUEIDENTIFIER NOT NULL,
    quantity BIGINT NOT NULL,
    unit_price_amount DECIMAL(19,0) NOT NULL,
    CONSTRAINT FK_commerce_order_item_order FOREIGN KEY (order_id) REFERENCES commerce_order(id),
    CONSTRAINT FK_commerce_order_item_variant FOREIGN KEY (variant_public_id) REFERENCES catalog_product_variant(public_id),
    CONSTRAINT FK_commerce_order_item_location FOREIGN KEY (location_public_id) REFERENCES org_location(public_id),
    CONSTRAINT CK_commerce_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT CK_commerce_order_item_price CHECK (unit_price_amount > 0),
    CONSTRAINT CK_commerce_order_item_total CHECK (unit_price_amount * quantity <= 9223372036854775807)
);

INSERT INTO iam_permission(code) VALUES ('ORDER_PLACE');

INSERT INTO iam_role_permission(role_id, permission_id)
SELECT roles.id, permissions.id
FROM iam_role_bundle roles
CROSS JOIN iam_permission permissions
WHERE roles.code = 'CUSTOMER' AND permissions.code = 'ORDER_PLACE';
