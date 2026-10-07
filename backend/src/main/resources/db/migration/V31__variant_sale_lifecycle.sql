ALTER TABLE catalog_product_variant DROP CONSTRAINT CK_catalog_variant_status;

ALTER TABLE catalog_product_variant ADD CONSTRAINT CK_catalog_variant_status
    CHECK (lifecycle_status IN ('DRAFT', 'PUBLISHED', 'RETIRED'));
