SET NOCOUNT ON;

WITH normalized AS (
    SELECT products.public_id AS product_public_id, variants.id,
           variants.public_id AS variant_public_id, variants.sku, variants.color, variants.size,
           variants.lifecycle_status,
           UPPER(LTRIM(RTRIM(variants.color)) COLLATE Latin1_General_100_CI_AS)
                COLLATE Latin1_General_100_BIN2 AS color_key,
           TRY_CONVERT(DECIMAL(5,2), LTRIM(RTRIM(variants.size))) AS size_value
    FROM catalog_product_variant variants
    JOIN catalog_product products ON products.id = variants.product_id
), duplicate_keys AS (
    SELECT product_public_id, color_key, size_value
    FROM normalized
    WHERE lifecycle_status <> 'RETIRED'
    GROUP BY product_public_id, color_key, size_value
    HAVING COUNT_BIG(*) > 1
)
SELECT normalized.product_public_id, normalized.variant_public_id, normalized.sku,
       normalized.color, normalized.size, normalized.lifecycle_status,
       normalized.color_key, normalized.size_value,
       price.current_price, stock.on_hand, stock.reserved,
       reservations.active_reservations, orders.order_items
FROM normalized
JOIN duplicate_keys duplicates
  ON duplicates.product_public_id = normalized.product_public_id
 AND duplicates.color_key = normalized.color_key
 AND duplicates.size_value = normalized.size_value
OUTER APPLY (
    SELECT MAX(prices.amount) AS current_price
    FROM pricing_variant_price prices
    WHERE prices.variant_id = normalized.id AND prices.valid_to IS NULL
) price
OUTER APPLY (
    SELECT SUM(balances.on_hand) AS on_hand, SUM(balances.reserved) AS reserved
    FROM inventory_balance balances
    WHERE balances.variant_id = normalized.id
) stock
OUTER APPLY (
    SELECT COUNT_BIG(*) AS active_reservations
    FROM inventory_reservation reservations
    WHERE reservations.variant_id = normalized.id
      AND reservations.status IN ('ACTIVE', 'ADOPTED', 'COMMITTED')
) reservations
OUTER APPLY (
    SELECT COUNT_BIG(*) AS order_items
    FROM commerce_order_item items
    WHERE items.variant_public_id = normalized.variant_public_id
) orders
ORDER BY normalized.product_public_id, normalized.color_key, normalized.size_value, normalized.id;

SELECT public_id AS variant_public_id, sku, size
FROM catalog_product_variant
WHERE LTRIM(RTRIM(size)) COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9.]%'
   OR LEN(LTRIM(RTRIM(size))) - LEN(REPLACE(LTRIM(RTRIM(size)), '.', '')) > 1
   OR TRY_CONVERT(DECIMAL(38,18), LTRIM(RTRIM(size))) IS NULL
   OR TRY_CONVERT(DECIMAL(5,2), LTRIM(RTRIM(size))) IS NULL
   OR TRY_CONVERT(DECIMAL(38,18), LTRIM(RTRIM(size))) <= 0
   OR TRY_CONVERT(DECIMAL(38,18), LTRIM(RTRIM(size)))
        <> CONVERT(DECIMAL(38,18), TRY_CONVERT(DECIMAL(5,2), LTRIM(RTRIM(size))))
ORDER BY id;
