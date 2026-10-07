-- Preserve every existing financial allocation. The application explicitly writes SNAPSHOT_V2.
ALTER TABLE payment_void_attempt ADD calculation_version VARCHAR(16) NOT NULL
    CONSTRAINT DF_payment_void_attempt_calculation DEFAULT 'LEGACY_V1';
GO
ALTER TABLE payment_void_attempt ADD CONSTRAINT CK_payment_void_attempt_calculation
    CHECK (calculation_version IN ('LEGACY_V1','SNAPSHOT_V2'));

ALTER TABLE payment_void_allocation DROP CONSTRAINT CK_payment_void_allocation_component;
ALTER TABLE payment_void_allocation DROP CONSTRAINT UQ_payment_void_allocation_component;
ALTER TABLE payment_void_allocation DROP CONSTRAINT FK_payment_void_allocation_component;
DROP INDEX IX_payment_void_allocation_capacity ON payment_void_allocation;
ALTER TABLE payment_void_allocation ALTER COLUMN component_public_id UNIQUEIDENTIFIER NULL;
ALTER TABLE payment_void_allocation ADD shipping_order_public_id UNIQUEIDENTIFIER NULL;
GO
ALTER TABLE payment_void_allocation ADD
    CONSTRAINT FK_payment_void_allocation_component FOREIGN KEY(component_public_id) REFERENCES commerce_order_item(public_id),
    CONSTRAINT FK_payment_void_allocation_shipping FOREIGN KEY(shipping_order_public_id) REFERENCES commerce_order(public_id),
    CONSTRAINT CK_payment_void_allocation_component CHECK (
        (component_type='ORDER_ITEM' AND component_public_id IS NOT NULL AND shipping_order_public_id IS NULL)
        OR (component_type='SHIPPING' AND component_public_id IS NULL AND shipping_order_public_id IS NOT NULL));
CREATE UNIQUE INDEX UX_payment_void_allocation_item ON payment_void_allocation(void_attempt_id,component_public_id)
    WHERE component_public_id IS NOT NULL;
CREATE UNIQUE INDEX UX_payment_void_allocation_shipping ON payment_void_allocation(void_attempt_id,shipping_order_public_id)
    WHERE shipping_order_public_id IS NOT NULL;
CREATE INDEX IX_payment_void_allocation_capacity ON payment_void_allocation(component_public_id,status) INCLUDE(amount);
CREATE INDEX IX_payment_void_allocation_shipping_capacity ON payment_void_allocation(shipping_order_public_id,status) INCLUDE(amount);
