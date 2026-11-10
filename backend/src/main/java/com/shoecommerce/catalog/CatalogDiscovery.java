package com.shoecommerce.catalog;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CatalogDiscovery {
    private static final Pattern PRICE = Pattern.compile(
            "(?:\\bduoi\\b|\\bunder\\b|\\bless\\s+than\\b|<=)\\s*(\\d+(?:[.,]\\d+)?)\\s*(trieu|million|tr|m)?\\b");
    private static final Pattern SKU = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)+");
    private static final Set<String> STOP_WORDS = Set.of(
            "giay", "shoe", "shoes", "sneaker", "sneakers", "mau", "color", "colour",
            "toi", "muon", "mot", "doi", "di", "kieu", "tim", "looking", "for", "want");
    private static final Map<String, Set<String>> COLOR_ALIASES = Map.of(
            "WHITE", Set.of("trang", "white", "chalk"),
            "BLACK", Set.of("den", "black", "all black"),
            "CREAM", Set.of("kem", "cream", "ivory", "beige", "natural"),
            "NAVY", Set.of("xanh navy", "navy"));
    private static final Map<String, Set<String>> CATEGORY_ALIASES = Map.of(
            "RUNNING", Set.of("chay bo", "running", "road running"),
            "EVERYDAY", Set.of("hang ngay", "everyday", "daily", "lifestyle", "canvas", "smart casual", "casual daily"),
            "TRAIL", Set.of("dia hinh", "trail", "trail walking", "trail running"),
            "COURT", Set.of("court"),
            "TRAINING", Set.of("tap luyen", "training"),
            "SLIP_ON", Set.of("slip on", "loafer"));

    private CatalogDiscovery() { }

    static QueryIntent interpret(String rawQuery) {
        String normalized = normalize(rawQuery);
        String priceSource = fold(rawQuery);
        Matcher priceMatcher = PRICE.matcher(priceSource);
        Long maximumPrice = priceMatcher.find() ? price(priceMatcher.group(1), priceMatcher.group(2)) : null;
        Set<String> colors = recognized(normalized, COLOR_ALIASES);
        Set<String> categories = recognized(normalized, CATEGORY_ALIASES);
        String sku = SKU.matcher(normalized).matches() ? normalized : null;

        String residual = normalized;
        if (maximumPrice != null) residual = normalize(PRICE.matcher(priceSource).replaceAll(" "));
        residual = removeRecognizedPhrases(residual, colors, COLOR_ALIASES);
        residual = removeRecognizedPhrases(residual, categories, CATEGORY_ALIASES);
        List<String> terms = List.of(residual.split(" ")).stream()
                .filter(token -> !token.isBlank() && !STOP_WORDS.contains(token) && !token.matches("\\d+(?:[.,]\\d+)?"))
                .distinct().toList();
        return new QueryIntent(normalized, colors, categories, maximumPrice, terms, sku);
    }

    static Outcome rank(QueryIntent intent, List<SearchDocument> documents) {
        Comparator<Scored> order = Comparator.comparingInt(Scored::score).reversed()
                .thenComparingInt(scored -> scored.document().merchandisingRank())
                .thenComparing(scored -> scored.document().name())
                .thenComparing(scored -> scored.document().productId());

        if (intent.normalizedQuery().isBlank()) {
            return new Outcome(documents.stream()
                    .sorted(Comparator.comparingInt(SearchDocument::merchandisingRank)
                            .thenComparing(SearchDocument::name).thenComparing(SearchDocument::productId))
                    .map(SearchDocument::productId).toList(), List.of());
        }

        List<Scored> matches = documents.stream()
                .map(document -> new Scored(document, matchScore(intent, document)))
                .filter(scored -> scored.score() > 0)
                .sorted(order).toList();
        if (!matches.isEmpty()) {
            return new Outcome(matches.stream().map(scored -> scored.document().productId()).toList(), List.of());
        }

        if (intent.sku() == null && !intent.terms().isEmpty()) {
            List<UUID> typoSuggestions = documents.stream()
                    .filter(document -> document.availableVariantCount() > 0)
                    .filter(document -> fuzzyNameMatch(intent, document))
                    .sorted(Comparator.comparingInt(SearchDocument::merchandisingRank)
                            .thenComparing(SearchDocument::name).thenComparing(SearchDocument::productId))
                    .limit(6).map(SearchDocument::productId).toList();
            if (!typoSuggestions.isEmpty()) return new Outcome(List.of(), typoSuggestions);
        }

        List<UUID> suggestions = documents.stream()
                .filter(document -> document.availableVariantCount() > 0)
                .map(document -> new Scored(document, suggestionScore(intent, document)))
                .sorted(order)
                .limit(6)
                .map(scored -> scored.document().productId()).toList();
        return new Outcome(List.of(), suggestions);
    }

    private static int matchScore(QueryIntent intent, SearchDocument document) {
        boolean exactSku = intent.sku() != null && document.skus().stream()
                .map(CatalogDiscovery::normalize).anyMatch(intent.sku()::equals);
        if (exactSku) return 1_100;
        String name = normalize(document.name());
        boolean exactName = name.equals(intent.normalizedQuery());
        if (exactName) return 1_000;
        if (intent.maximumPrice() != null && document.fromAmount() > intent.maximumPrice()) return 0;
        Set<String> documentColors = recognized(String.join(" ", document.colors()), COLOR_ALIASES);
        if (!documentColors.containsAll(intent.colorFamilies())) return 0;
        Set<String> documentCategories = recognized(document.category() + " " + document.collection() + " "
                + String.join(" ", document.attributes()), CATEGORY_ALIASES);
        if (!documentCategories.containsAll(intent.categories())) return 0;

        String searchable = normalize(document.name() + " " + document.category() + " " + document.collection()
                + " " + String.join(" ", document.colors()) + " " + String.join(" ", document.attributes()));
        boolean nameTerms = intent.terms().isEmpty() || intent.terms().stream().allMatch(name::contains);
        boolean allTerms = intent.terms().isEmpty() || intent.terms().stream().allMatch(searchable::contains);
        if (!allTerms) return 0;
        if (intent.terms().isEmpty() && intent.colorFamilies().isEmpty() && intent.categories().isEmpty()
                && intent.maximumPrice() == null) return 0;

        int score = nameTerms ? 650 : 250;
        score += intent.colorFamilies().size() * 120;
        score += intent.categories().size() * 120;
        if (intent.maximumPrice() != null) score += 40;
        return score;
    }

    private static int suggestionScore(QueryIntent intent, SearchDocument document) {
        Set<String> colors = recognized(String.join(" ", document.colors()), COLOR_ALIASES);
        Set<String> categories = recognized(document.category() + " " + document.collection() + " "
                + String.join(" ", document.attributes()), CATEGORY_ALIASES);
        String searchable = normalize(document.name() + " " + document.category() + " " + document.collection());
        int score = intersectionSize(intent.colorFamilies(), colors) * 40
                + intersectionSize(intent.categories(), categories) * 30;
        score += (int) intent.terms().stream().filter(searchable::contains).count() * 20;
        if (intent.maximumPrice() != null && document.fromAmount() <= intent.maximumPrice()) score += 10;
        return score;
    }

    private static boolean fuzzyNameMatch(QueryIntent intent, SearchDocument document) {
        String name = normalize(document.name());
        String[] nameWords = name.split(" ");
        if (intent.terms().stream().filter(term -> term.length() >= 4)
                .noneMatch(term -> java.util.Arrays.stream(nameWords).anyMatch(word -> oneEditApart(term, word)))) {
            return false;
        }
        String searchable = normalize(document.name() + " " + document.category() + " " + document.collection()
                + " " + String.join(" ", document.colors()) + " " + String.join(" ", document.attributes()));
        int typos = 0;
        for (String term : intent.terms()) {
            if (searchable.contains(term)) continue;
            if (term.length() < 4 || java.util.Arrays.stream(nameWords)
                    .noneMatch(word -> oneEditApart(term, word))) return false;
            if (++typos > 1) return false;
        }
        if (typos != 1) return false;
        if (intent.maximumPrice() != null && document.fromAmount() > intent.maximumPrice()) return false;
        if (!recognized(String.join(" ", document.colors()), COLOR_ALIASES)
                .containsAll(intent.colorFamilies())) return false;
        if (!recognized(document.category() + " " + document.collection() + " "
                + String.join(" ", document.attributes()), CATEGORY_ALIASES)
                .containsAll(intent.categories())) return false;
        return true;
    }

    // One insertion, deletion, substitution or adjacent swap; short tokens remain exact-only.
    private static boolean oneEditApart(String left, String right) {
        if (Math.abs(left.length() - right.length()) > 1) return false;
        int i = 0, j = 0, edits = 0;
        while (i < left.length() && j < right.length()) {
            if (left.charAt(i) == right.charAt(j)) { i++; j++; continue; }
            if (++edits > 1) return false;
            if (left.length() == right.length() && i + 1 < left.length() && j + 1 < right.length()
                    && left.charAt(i) == right.charAt(j + 1)
                    && left.charAt(i + 1) == right.charAt(j)) { i += 2; j += 2; }
            else if (left.length() < right.length()) j++;
            else if (left.length() > right.length()) i++;
            else { i++; j++; }
        }
        return edits + (i < left.length() || j < right.length() ? 1 : 0) == 1;
    }

    private static int intersectionSize(Set<String> first, Set<String> second) {
        return (int) first.stream().filter(second::contains).count();
    }

    private static Long price(String number, String unit) {
        try {
            double value = Double.parseDouble(number.replace(',', '.'));
            if (unit != null) value *= 1_000_000;
            if (value <= 0 || value > 1_000_000_000) return null;
            return Math.round(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Set<String> recognized(String source, Map<String, Set<String>> aliases) {
        String normalized = " " + normalize(source) + " ";
        Set<String> recognized = new LinkedHashSet<>();
        aliases.forEach((concept, phrases) -> {
            if (phrases.stream().map(CatalogDiscovery::normalize)
                    .anyMatch(phrase -> normalized.contains(" " + phrase + " "))) recognized.add(concept);
        });
        return Set.copyOf(recognized);
    }

    private static String removeRecognizedPhrases(String source, Set<String> concepts,
            Map<String, Set<String>> aliases) {
        String value = " " + source + " ";
        for (String concept : concepts) {
            List<String> phrases = aliases.getOrDefault(concept, Set.of()).stream()
                    .map(CatalogDiscovery::normalize)
                    .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                    .toList();
            for (String phrase : phrases) {
                value = value.replace(" " + phrase + " ", " ");
            }
        }
        return normalize(value);
    }

    static String normalize(String value) {
        String folded = fold(value).replaceAll("[^a-z0-9-]+", " ");
        return folded.trim().replaceAll("\\s+", " ");
    }

    private static String fold(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    record QueryIntent(String normalizedQuery, Set<String> colorFamilies, Set<String> categories,
            Long maximumPrice, List<String> terms, String sku) { }

    record SearchDocument(UUID productId, String name, String category, String collection,
            Set<String> colors, Set<String> skus, Set<String> attributes, long fromAmount,
            long availableVariantCount, int merchandisingRank) {
        SearchDocument {
            colors = Set.copyOf(colors);
            skus = Set.copyOf(skus);
            attributes = Set.copyOf(attributes);
        }
    }

    record Outcome(List<UUID> resultIds, List<UUID> suggestionIds) { }

    private record Scored(SearchDocument document, int score) { }
}
