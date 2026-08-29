package com.karvya.store.application.catalog.dto;

import com.karvya.store.domain.model.ProductImage;

/**
 * A photograph as the storefront needs it.
 *
 * <p>Only the base key travels. The client composes
 * {@code /media/{key}-{width}.{format}} for its own srcset, so widths and
 * formats can change in the pipeline without an API change. Intrinsic
 * dimensions are included so the browser can reserve space and avoid layout
 * shift before the image loads.
 */
public record ImageRef(
        String key,
        String alt,
        Integer width,
        Integer height
) {
    public static ImageRef from(ProductImage image) {
        return new ImageRef(
                image.getStorageKey(),
                image.getAltText(),
                image.getWidth(),
                image.getHeight()
        );
    }
}
