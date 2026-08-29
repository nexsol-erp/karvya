package com.karvya.store.application.media;

import com.karvya.store.domain.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

/**
 * Turns one uploaded photograph into the set of files the storefront asks for.
 *
 * <p>The storefront requests a width, not an original: {@code /media/{key}-800.jpg}.
 * An upload stored as a single file would therefore be requested at a URL that
 * was never written, and show as a broken image. This writes one JPEG per width
 * so every URL in the srcset resolves.
 *
 * <p>JPEG only. AVIF and WebP need a native encoder that a plain JRE does not
 * have, and shipping one would put a platform-specific binary in the runtime
 * image. The renditions that exist are recorded against the row rather than
 * assumed, so the storefront offers only what was actually written.
 */
@Service
public class ImageRenditionService {

    private static final Logger log = LoggerFactory.getLogger(ImageRenditionService.class);

    private static final float JPEG_QUALITY = 0.82f;

    private final StorageService storage;

    public ImageRenditionService(StorageService storage) {
        this.storage = storage;
    }

    /** The base key the renditions were written under. */
    public record Stored(String baseKey, String formats) {
    }

    /**
     * Writes every rendition and returns the base key they share.
     *
     * @param keyPrefix logical folder, e.g. {@code products/kv-bh-01}
     */
    public Stored store(String keyPrefix, byte[] original) {
        BufferedImage source = decode(original);
        String baseKey = keyPrefix + "/" + UUID.randomUUID();

        for (int width : ImageRenditions.WIDTHS) {
            // never upscale: a narrow original is written at its own size under
            // the wider name, so the URL resolves without inventing detail
            int target = Math.min(width, source.getWidth());
            byte[] encoded = encodeJpeg(resize(source, target));
            storage.storeAt(ImageRenditions.key(baseKey, width, "jpg"),
                    new ByteArrayInputStream(encoded));
        }

        log.info("Stored {} renditions under {}", ImageRenditions.WIDTHS.size(), baseKey);
        return new Stored(baseKey, ImageRenditions.UPLOAD_FORMATS);
    }

    private BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                // reachable only if the accepted formats and what ImageIO can
                // actually decode ever drift apart
                throw new ConflictException("unreadable-image",
                        "That image could not be read. Please upload a JPEG or PNG.");
            }
            return image;
        } catch (IOException e) {
            throw new ConflictException("unreadable-image",
                    "That image could not be read. Please upload a JPEG or PNG.");
        }
    }

    /**
     * Halves repeatedly rather than scaling to the target in one step.
     *
     * <p>A single large reduction samples too few of the source pixels and
     * comes out visibly harsh - on coir, which is all fine fibre texture, that
     * shows as speckling.
     */
    private BufferedImage resize(BufferedImage source, int targetWidth) {
        int targetHeight = Math.max(1,
                Math.round(source.getHeight() * (targetWidth / (float) source.getWidth())));

        BufferedImage current = source;
        int width = source.getWidth();
        int height = source.getHeight();

        while (width / 2 > targetWidth) {
            width /= 2;
            height = Math.max(1, height / 2);
            current = draw(current, width, height);
        }

        return draw(current, targetWidth, targetHeight);
    }

    private BufferedImage draw(BufferedImage source, int width, int height) {
        // TYPE_INT_RGB, and a white fill first: JPEG has no alpha channel, so a
        // transparent PNG would otherwise composite onto black
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private byte[] encodeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG writer is available in this runtime");
        }

        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);

            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(JPEG_QUALITY);
            }

            writer.write(null, new IIOImage(image, null, null), params);
            stream.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode the image", e);
        } finally {
            writer.dispose();
        }
    }
}
