ALTER TABLE catalog_product_variant ADD
    barcode NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NULL,
    ever_published BIT NOT NULL CONSTRAINT DF_catalog_variant_ever_published DEFAULT 0 WITH VALUES;

GO

UPDATE catalog_product_variant
SET ever_published = 1
WHERE lifecycle_status IN ('PUBLISHED', 'RETIRED');

ALTER TABLE catalog_product_variant ADD CONSTRAINT CK_catalog_variant_barcode_trimmed
    CHECK (barcode IS NULL OR (LEN(barcode) BETWEEN 1 AND 128 AND barcode = LTRIM(RTRIM(barcode))));

CREATE UNIQUE INDEX UX_catalog_product_variant_barcode
    ON catalog_product_variant(barcode)
    WHERE barcode IS NOT NULL;
