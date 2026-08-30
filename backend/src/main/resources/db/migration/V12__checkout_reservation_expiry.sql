ALTER TABLE inventory_reservation ADD
    expires_at DATETIME2(6) NULL,
    expired_at DATETIME2(6) NULL;

GO

ALTER TABLE inventory_reservation DROP CONSTRAINT CK_inventory_reservation_lifecycle_time;
ALTER TABLE inventory_reservation DROP CONSTRAINT CK_inventory_reservation_status;

ALTER TABLE inventory_reservation ADD CONSTRAINT CK_inventory_reservation_status
    CHECK (status IN ('ACTIVE', 'ADOPTED', 'RELEASED', 'CONSUMED', 'EXPIRED'));

ALTER TABLE inventory_reservation ADD CONSTRAINT CK_inventory_reservation_expiry
    CHECK (expires_at IS NULL OR expires_at > created_at);

ALTER TABLE inventory_reservation ADD CONSTRAINT CK_inventory_reservation_lifecycle_time
    CHECK (
        (status = 'ACTIVE' AND adopted_at IS NULL AND released_at IS NULL AND consumed_at IS NULL AND expired_at IS NULL)
        OR (status = 'ADOPTED' AND adopted_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NULL AND expired_at IS NULL)
        OR (status = 'RELEASED' AND released_at IS NOT NULL AND consumed_at IS NULL AND expired_at IS NULL)
        OR (status = 'CONSUMED' AND adopted_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NOT NULL AND expired_at IS NULL)
        OR (status = 'EXPIRED' AND expires_at IS NOT NULL AND released_at IS NULL AND consumed_at IS NULL AND expired_at IS NOT NULL)
    );

CREATE INDEX IX_inventory_reservation_expiry
    ON inventory_reservation(status, expires_at)
    INCLUDE (public_id, variant_id, location_id, quantity);
