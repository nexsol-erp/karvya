package com.karvya.store.application.catalog;

import java.math.BigDecimal;

/**
 * Filters for a catalogue search. Blank strings are normalised to null on
 * construction so that an empty query box behaves as no filter at all.
 */
public record ProductQuery(
        String q,
        String categorySlug,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean featured,
        boolean inStockOnly,
        ProductSort sort,
        int page,
        int size
) {

    public static final int MAX_PAGE_SIZE = 48;

    public ProductQuery {
        q = blankToNull(q);
        categorySlug = blankToNull(categorySlug);
        sort = sort == null ? ProductSort.RELEVANCE : sort;
        page = Math.max(0, page);
        size = size <= 0 ? 12 : Math.min(size, MAX_PAGE_SIZE);

        // a reversed price range is a user error, not an empty result
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            BigDecimal swap = minPrice;
            minPrice = maxPrice;
            maxPrice = swap;
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
