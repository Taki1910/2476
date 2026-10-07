CREATE TABLE geo_ward (
    code VARCHAR(12) NOT NULL CONSTRAINT PK_geo_ward PRIMARY KEY,
    province_code VARCHAR(12) NOT NULL,
    district_code VARCHAR(12) NOT NULL,
    display_label NVARCHAR(120) NOT NULL,
    enabled BIT NOT NULL CONSTRAINT DF_geo_ward_enabled DEFAULT 1,
    dataset_version VARCHAR(32) NOT NULL,
    CONSTRAINT FK_geo_ward_district FOREIGN KEY (province_code, district_code)
        REFERENCES geo_district(province_code, code),
    CONSTRAINT UQ_geo_ward_hierarchy UNIQUE (province_code, district_code, code),
    CONSTRAINT CK_geo_ward_label CHECK (LEN(display_label) BETWEEN 1 AND 120)
);
CREATE INDEX IX_geo_ward_district_enabled
    ON geo_ward(province_code, district_code, enabled, display_label);

-- Deliberately limited demo hierarchy. These rows are selectable examples,
-- not geocoding evidence that a submitted street address physically exists.
INSERT INTO geo_ward(code, province_code, district_code, display_label, dataset_version) VALUES
    ('DEMO-001-01', '01', '001', N'Phường Phúc Xá', 'DEMO-2026'),
    ('DEMO-005-01', '01', '005', N'Phường Nghĩa Đô', 'DEMO-2026'),
    ('DEMO-017-01', '01', '017', N'Thị trấn Đông Anh', 'DEMO-2026'),
    ('DEMO-760-01', '79', '760', N'Phường Bến Nghé', 'DEMO-2026'),
    ('DEMO-769-01', '79', '769', N'Phường Thảo Điền', 'DEMO-2026'),
    ('DEMO-492-01', '48', '492', N'Phường Hải Châu I', 'DEMO-2026');

ALTER TABLE pricing_cart_quote ADD destination_ward_code VARCHAR(12) NULL;
ALTER TABLE pricing_cart_quote ADD
    CONSTRAINT FK_pricing_cart_quote_destination_ward
        FOREIGN KEY (destination_province_code, destination_district_code, destination_ward_code)
        REFERENCES geo_ward(province_code, district_code, code),
    CONSTRAINT CK_pricing_cart_quote_ward_evidence CHECK (
        destination_ward_code IS NULL OR fulfillment_type='DELIVERY');

ALTER TABLE commerce_order ADD destination_ward_code VARCHAR(12) NULL;
ALTER TABLE commerce_order ADD
    CONSTRAINT FK_commerce_order_destination_ward
        FOREIGN KEY (destination_province_code, destination_district_code, destination_ward_code)
        REFERENCES geo_ward(province_code, district_code, code),
    CONSTRAINT CK_commerce_order_ward_evidence CHECK (
        destination_ward_code IS NULL OR channel='ONLINE');

ALTER TABLE pickup_fulfillment ADD
    delivery_province_code VARCHAR(12) NULL,
    delivery_province_label NVARCHAR(120) NULL,
    delivery_district_code VARCHAR(12) NULL,
    delivery_district_label NVARCHAR(120) NULL,
    delivery_ward_code VARCHAR(12) NULL,
    delivery_ward_label NVARCHAR(120) NULL,
    delivery_address_line NVARCHAR(500) NULL,
    address_resolution_version VARCHAR(32) NULL;

GO

ALTER TABLE pickup_fulfillment ADD
    CONSTRAINT CK_pickup_fulfillment_address_snapshot CHECK (
        (address_resolution_version IS NULL
            AND delivery_province_code IS NULL AND delivery_province_label IS NULL
            AND delivery_district_code IS NULL AND delivery_district_label IS NULL
            AND delivery_ward_code IS NULL AND delivery_ward_label IS NULL
            AND delivery_address_line IS NULL)
        OR
        (fulfillment_type='DELIVERY'
            AND LEN(address_resolution_version) BETWEEN 1 AND 32
            AND LEN(delivery_province_code) BETWEEN 1 AND 12
            AND LEN(delivery_province_label) BETWEEN 1 AND 120
            AND LEN(delivery_district_code) BETWEEN 1 AND 12
            AND LEN(delivery_district_label) BETWEEN 1 AND 120
            AND LEN(delivery_ward_code) BETWEEN 1 AND 12
            AND LEN(delivery_ward_label) BETWEEN 1 AND 120
            AND LEN(delivery_address_line) BETWEEN 1 AND 500));
