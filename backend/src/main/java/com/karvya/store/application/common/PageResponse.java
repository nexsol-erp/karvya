package com.karvya.store.application.common;

import org.springframework.data.domain.Page;
import java.util.List;
import java.util.function.Function;

/**
 * Pagination envelope returned by every list endpoint. Deliberately not
 * Spring's {@code Page}, whose JSON shape is unstable across versions and
 * leaks sort internals into the public contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
