ALTER TABLE pickup_fulfillment ADD picking_started_at DATETIME2(6) NULL;

GO

ALTER TABLE pickup_fulfillment DROP CONSTRAINT CK_pickup_fulfillment_status;

ALTER TABLE pickup_fulfillment ADD CONSTRAINT CK_pickup_fulfillment_status
    CHECK (status IN ('PENDING', 'PICKING'));

ALTER TABLE pickup_fulfillment ADD CONSTRAINT CK_pickup_fulfillment_lifecycle_time
    CHECK (
        (status = 'PENDING' AND picking_started_at IS NULL)
        OR (status = 'PICKING' AND picking_started_at IS NOT NULL)
    );
