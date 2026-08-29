package com.karvya.store.application.media;

import java.util.List;

/**
 * The shape of the media library, in one place.
 *
 * <p>A {@code storage_key} names a base rather than a file. The storefront asks
 * for {@code /media/{key}-{width}.{format}}, so these widths and this naming
 * have to agree with three other things: {@code scripts/optimise-images.mjs},
 * which produced everything seeded; {@code ProductImage.tsx}, which builds the
 * srcset; and {@link ImageRenditionService}, which writes uploads. Changing a
 * width here without changing those leaves URLs pointing at files that were
 * never written.
 */
public final class ImageRenditions {

    /** Widest last, matching the srcset the storefront emits. */
    public static final List<Integer> WIDTHS = List.of(480, 800, 1280, 1856);

    /** What the offline pipeline produces. */
    public static final String OFFLINE_FORMATS = "avif,webp,jpg";

    /** What the application can encode without a native library. */
    public static final String UPLOAD_FORMATS = "jpg";

    private ImageRenditions() {
    }

    public static String key(String baseKey, int width, String extension) {
        return baseKey + "-" + width + "." + extension;
    }

    /** Every file backing one image, so deleting a photograph leaves nothing behind. */
    public static List<String> allKeys(String baseKey, String formats) {
        return java.util.Arrays.stream(formats.split(","))
                .map(String::trim)
                .filter(format -> !format.isEmpty())
                .flatMap(format -> WIDTHS.stream().map(width -> key(baseKey, width, format)))
                .toList();
    }
}
