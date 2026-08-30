package com.karvya.store.application.media;

import com.karvya.store.domain.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * The shop's own mark.
 *
 * <p>Kept apart from the product pipeline because the requirements are opposite.
 * A photograph is opaque and wants JPEG; a logo is usually a transparent PNG,
 * and JPEG has no alpha channel - the product path fills white behind it, which
 * would put a white rectangle around the mark on any coloured header. Every
 * rendition here is a PNG and every one keeps its transparency.
 */
@Service
public class LogoService {

    private static final Logger log = LoggerFactory.getLogger(LogoService.class);

    /** Enough for a retina header without carrying a print-sized file. */
    private static final int DISPLAY_WIDTH = 512;

    /**
     * Square sizes for the browser tab and the home-screen icon.
     *
     * <p>A mark is drawn into a square canvas rather than stretched to fill it.
     * Most shop logos are wider than they are tall, and a favicon that squashes
     * the wordmark to fit is worse than one with transparent space around it.
     */
    private static final List<Integer> ICON_SIZES = List.of(32, 180);

    private final StorageService storage;

    public LogoService(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Writes the display image and the icons, returning the key they share.
     *
     * <p>The storefront asks for {@code /media/{key}.png} and
     * {@code /media/{key}-32.png}.
     */
    public String store(byte[] original) {
        BufferedImage source = decode(original);
        String key = "branding/" + UUID.randomUUID();

        // never upscaled: a small mark stays its own size rather than being
        // blown up into something blurred
        int width = Math.min(DISPLAY_WIDTH, source.getWidth());
        int height = Math.max(1, Math.round(source.getHeight() * (width / (float) source.getWidth())));
        storage.storeAt(key + ".png", new ByteArrayInputStream(encode(scaled(source, width, height))));

        for (int size : ICON_SIZES) {
            storage.storeAt(key + "-" + size + ".png",
                    new ByteArrayInputStream(encode(squared(source, size))));
        }

        log.info("Stored logo {} and {} icons", key, ICON_SIZES.size());
        return key;
    }

    /** Every file behind one logo, so replacing it leaves nothing behind. */
    public List<String> keysFor(String key) {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(key + ".png"),
                ICON_SIZES.stream().map(size -> key + "-" + size + ".png")).toList();
    }

    public void delete(String key) {
        keysFor(key).forEach(storage::delete);
        log.info("Removed logo {}", key);
    }

    private BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new ConflictException("unreadable-image",
                        "That image could not be read. Please upload a PNG or JPEG.");
            }
            return image;
        } catch (IOException e) {
            throw new ConflictException("unreadable-image",
                    "That image could not be read. Please upload a PNG or JPEG.");
        }
    }

    /** Scaled to an exact size, alpha intact. */
    private BufferedImage scaled(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            quality(g);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** Centred in a transparent square, keeping its proportions. */
    private BufferedImage squared(BufferedImage source, int size) {
        float scale = Math.min(size / (float) source.getWidth(), size / (float) source.getHeight());
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));

        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            quality(g);
            g.drawImage(source, (size - width) / 2, (size - height) / 2, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private byte[] encode(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode the logo", e);
        }
        return out.toByteArray();
    }
}
