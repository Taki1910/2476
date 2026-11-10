DECLARE @invalid_sizes NVARCHAR(1800);
SELECT @invalid_sizes = STRING_AGG(CONCAT(sample.sku, N'=', sample.size), N'; ')
FROM (
    SELECT TOP (12) sku, size
    FROM catalog_product_variant
    WHERE LTRIM(RTRIM(size)) COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9.]%'
       OR LEN(LTRIM(RTRIM(size))) - LEN(REPLACE(LTRIM(RTRIM(size)), '.', '')) > 1
       OR TRY_CONVERT(DECIMAL(38,18), LTRIM(RTRIM(size))) IS NULL
       OR TRY_CONVERT(DECIMAL(5,2), LTRIM(RTRIM(size))) IS NULL
       OR TRY_CONVERT(DECIMAL(38,18), LTRIM(RTRIM(size))) <= 0
       OR TRY_CONVERT(DECIMAL(38,18), LTRIM(RTRIM(size)))
            <> CONVERT(DECIMAL(38,18), TRY_CONVERT(DECIMAL(5,2), LTRIM(RTRIM(size))))
    ORDER BY id
) sample;

IF @invalid_sizes IS NOT NULL
BEGIN
    DECLARE @invalid_message NVARCHAR(2048) = CONCAT(
        N'V32 blocked by invalid EU sizes: ', @invalid_sizes,
        N'. Run scripts/sql/p2b-v32-preflight.sql and repair explicitly. No V32 schema changes were applied.');
    THROW 51032, @invalid_message, 1;
END;

DECLARE @duplicate_options NVARCHAR(1800);
WITH normalized AS (
    SELECT products.public_id AS product_public_id, variants.id, variants.sku,
           variants.lifecycle_status,
           UPPER(LTRIM(RTRIM(variants.color)) COLLATE Latin1_General_100_CI_AS)
                COLLATE Latin1_General_100_BIN2 AS color_key,
           TRY_CONVERT(DECIMAL(5,2), LTRIM(RTRIM(variants.size))) AS size_value,
           COUNT_BIG(*) OVER (
               PARTITION BY variants.product_id,
                   UPPER(LTRIM(RTRIM(variants.color)) COLLATE Latin1_General_100_CI_AS)
                       COLLATE Latin1_General_100_BIN2,
                   TRY_CONVERT(DECIMAL(5,2), LTRIM(RTRIM(variants.size)))
           ) AS option_count
    FROM catalog_product_variant variants
    JOIN catalog_product products ON products.id = variants.product_id
    WHERE variants.lifecycle_status <> 'RETIRED'
)
SELECT @duplicate_options = STRING_AGG(CONCAT(sample.product_public_id, N'/', sample.color_key,
        N'/', sample.size_value, N'/', sample.sku, N'(', sample.lifecycle_status, N')'), N'; ')
FROM (
    SELECT TOP (12) product_public_id, color_key, size_value, sku, lifecycle_status
    FROM normalized
    WHERE option_count > 1
    ORDER BY id
) sample;

IF @duplicate_options IS NOT NULL
BEGIN
    DECLARE @duplicate_message NVARCHAR(2048) = CONCAT(
        N'V32 blocked by active duplicate Product/color/size options: ', @duplicate_options,
        N'. Run scripts/sql/p2b-v32-preflight.sql and resolve explicitly. No V32 schema changes were applied.');
    THROW 51033, @duplicate_message, 1;
END;

ALTER TABLE catalog_product_variant ADD
    normalized_color AS (
        UPPER(LTRIM(RTRIM(color)) COLLATE Latin1_General_100_CI_AS)
            COLLATE Latin1_General_100_BIN2
    ) PERSISTED,
    normalized_size AS TRY_CONVERT(DECIMAL(5,2), LTRIM(RTRIM(size))) PERSISTED;

GO

ALTER TABLE catalog_product_variant ADD CONSTRAINT CK_catalog_variant_normalized_size
    CHECK (normalized_size IS NOT NULL AND normalized_size > 0);

CREATE UNIQUE INDEX UX_catalog_variant_active_option
    ON catalog_product_variant(product_id, normalized_color, normalized_size)
    WHERE lifecycle_status <> 'RETIRED';
