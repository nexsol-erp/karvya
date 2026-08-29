package com.karvya.store.application.catalog;

import org.springframework.data.domain.Sort;

/**
 * The sort options offered in the shop. Restricting sorting to a closed set
 * keeps arbitrary column names out of the query and keeps results stable:
 * every option ends in a unique tiebreak so paging cannot repeat or skip rows.
 */
public enum ProductSort {

    /** Default ordering: featured pieces first, then alphabetical. */
    RELEVANCE(Sort.by(Sort.Order.desc("featured"), Sort.Order.asc("name"), Sort.Order.asc("id"))),

    NEWEST(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))),

    PRICE_ASC(Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"))),

    PRICE_DESC(Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id")));

    private final Sort sort;

    ProductSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }

    /** Falls back to {@link #RELEVANCE} for an unknown or absent value. */
    public static ProductSort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return RELEVANCE;
        }
        try {
            return valueOf(raw.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return RELEVANCE;
        }
    }
}
