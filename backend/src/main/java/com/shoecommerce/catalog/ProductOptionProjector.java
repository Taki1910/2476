package com.shoecommerce.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProductOptionProjector {
    private ProductOptionProjector() { }

    static List<StorefrontCatalogService.ColorOption> project(
            List<StorefrontCatalogService.VariantView> variants) {
        List<String> colors = variants.stream().map(StorefrontCatalogService.VariantView::color)
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        List<String> sizes = variants.stream().map(StorefrontCatalogService.VariantView::size)
                .distinct().sorted(ProductOptionProjector::compareSize).toList();
        Map<String, StorefrontCatalogService.VariantView> byOption = new LinkedHashMap<>();
        variants.forEach(variant -> byOption.putIfAbsent(key(variant.color(), variant.size()), variant));

        List<StorefrontCatalogService.ColorOption> projected = new ArrayList<>();
        for (String color : colors) {
            List<StorefrontCatalogService.SizeOption> sizeOptions = new ArrayList<>();
            for (String size : sizes) {
                StorefrontCatalogService.VariantView variant = byOption.get(key(color, size));
                List<String> alternatives = colors.stream()
                        .filter(alternative -> !alternative.equals(color))
                        .filter(alternative -> {
                            StorefrontCatalogService.VariantView candidate = byOption.get(key(alternative, size));
                            return candidate != null && "AVAILABLE".equals(candidate.availability());
                        }).toList();
                sizeOptions.add(new StorefrontCatalogService.SizeOption(size,
                        variant == null ? null : variant.id(), variant == null ? null : variant.sku(),
                        variant == null ? "UNAVAILABLE" : variant.availability(),
                        variant == null ? null : variant.amount(), alternatives));
            }
            projected.add(new StorefrontCatalogService.ColorOption(color, List.copyOf(sizeOptions)));
        }
        return List.copyOf(projected);
    }

    private static String key(String color, String size) {
        return color + '\u0000' + size;
    }

    private static int compareSize(String left, String right) {
        BigDecimal leftNumber = number(left);
        BigDecimal rightNumber = number(right);
        if (leftNumber != null && rightNumber != null) return leftNumber.compareTo(rightNumber);
        if (leftNumber != null) return -1;
        if (rightNumber != null) return 1;
        return Comparator.<String>naturalOrder().compare(left, right);
    }

    private static BigDecimal number(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
