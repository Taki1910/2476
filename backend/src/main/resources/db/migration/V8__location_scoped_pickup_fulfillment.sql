CREATE TABLE pickup_fulfillment (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_pickup_fulfillment PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_pickup_fulfillment_public_id UNIQUE,
    order_id BIGINT NOT NULL CONSTRAINT UQ_pickup_fulfillment_order UNIQUE,
    branch_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_pickup_fulfillment_version DEFAULT 0,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT FK_pickup_fulfillment_order FOREIGN KEY (order_id) REFERENCES commerce_order(id),
    CONSTRAINT FK_pickup_fulfillment_branch FOREIGN KEY (branch_id) REFERENCES org_branch(id),
    CONSTRAINT FK_pickup_fulfillment_location_branch FOREIGN KEY (location_id, branch_id) REFERENCES org_location(id, branch_id),
    CONSTRAINT CK_pickup_fulfillment_status CHECK (status = 'PENDING')
);
