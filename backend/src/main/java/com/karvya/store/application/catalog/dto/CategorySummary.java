package com.karvya.store.application.catalog.dto;

/** A category with the number of publicly visible products it holds. */
public record CategorySummary(
        Long id,
        String name,
        String slug,
        String description,
        String imageKey,
        long productCount
) {
}
