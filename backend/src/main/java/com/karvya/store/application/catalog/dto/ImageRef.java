package com.karvya.store.application.catalog.dto;

import com.karvya.store.domain.model.ProductImage;

import java.util.Arrays;
import java.util.List;

/**
 * A photograph as the storefront needs it.
 *
 * <p>Only the base key travels. The client composes
 * {@code /media/{key}-{width}.{format}} for its own srcset.
 *
 * <p>{@code formats} says which of those were actually written. It cannot be
 * inferred: photographs seeded through the offline pipeline have AVIF, WebP and
 * JPEG, while ones uploaded through the admin have JPEG alone, and a browser
 * picks a {@code <source>} on its type without checking the file is there - so
 * offering a format that was never written shows a broken image rather than
 * falling back to the next one.
 *
 * <p>Intrinsic dimensions are included so the browser can reserve space and
 * avoid layout shift before the image loads.
 */
public record ImageRef(
        String key,
        String alt,
        Integer width,
        Integer height,
        List<String> formats
) {
    public static ImageRef from(ProductImage image) {
        return new ImageRef(
                image.getStorageKey(),
                image.getAltText(),
                image.getWidth(),
                image.getHeight(),
                Arrays.stream(image.getFormats().split(","))
                        .map(String::trim)
                        .filter(format -> !format.isEmpty())
                        .toList()
        );
    }
}
