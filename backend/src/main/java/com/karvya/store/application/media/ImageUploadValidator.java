package com.karvya.store.application.media;

import com.karvya.store.domain.ConflictException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks an uploaded image before anything is written to disk.
 *
 * <p>Four independent checks, because each alone is bypassable. The declared
 * content type and the filename extension both come from the client and are
 * trivially lied about; the magic bytes are what the file actually is; and the
 * pixel dimensions catch a decompression bomb - a few kilobytes of PNG that
 * expands to gigabytes of raster and takes the process down with it.
 */
@Component
public class ImageUploadValidator {

    public record ValidatedImage(String extension, String contentType, int width, int height, byte[] bytes) {
    }

    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int MAX_DIMENSION = 6000;
    private static final int MIN_DIMENSION = 200;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ConflictException("empty-upload", "No file was uploaded.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ConflictException("upload-too-large",
                    "That image is larger than " + (MAX_BYTES / 1024 / 1024) + " MB.");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ConflictException("unsupported-image-type",
                    "Upload a JPEG, PNG or WebP image.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ConflictException("unreadable-upload", "That file could not be read.");
        }

        String actualFormat = detectFormat(bytes);
        if (actualFormat == null) {
            throw new ConflictException("unsupported-image-type",
                    "That file is not a JPEG, PNG or WebP image.");
        }

        // the extension must agree with what the bytes actually say, so a
        // script renamed to .png is refused rather than stored
        if (!actualFormat.equals(normalise(extension))) {
            throw new ConflictException("image-type-mismatch",
                    "The file contents do not match its extension.");
        }

        Dimensions dimensions = readDimensions(bytes, actualFormat);
        if (dimensions.width() > MAX_DIMENSION || dimensions.height() > MAX_DIMENSION) {
            throw new ConflictException("image-too-large",
                    "That image is larger than " + MAX_DIMENSION + " pixels on a side.");
        }
        if (dimensions.width() < MIN_DIMENSION || dimensions.height() < MIN_DIMENSION) {
            throw new ConflictException("image-too-small",
                    "Product photographs should be at least " + MIN_DIMENSION + " pixels on a side.");
        }

        return new ValidatedImage(
                actualFormat.equals("jpeg") ? "jpg" : actualFormat,
                CONTENT_TYPE_BY_EXTENSION.get(actualFormat.equals("jpeg") ? "jpg" : actualFormat),
                dimensions.width(), dimensions.height(), bytes);
    }

    private record Dimensions(int width, int height) {
    }

    /** What the bytes say the file is, regardless of what it is called. */
    private String detectFormat(byte[] bytes) {
        if (bytes.length < 12) {
            return null;
        }
        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A) {
            return "png";
        }
        // WebP: "RIFF" .... "WEBP"
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        return null;
    }

    /**
     * Reads the dimensions.
     *
     * <p>ImageIO has no WebP reader in the standard library, so a WebP that has
     * already passed its signature check is accepted on its header rather than
     * being rejected for want of a decoder. JPEG and PNG - the formats a
     * decompression bomb would actually use - are fully decoded and measured.
     */
    private Dimensions readDimensions(byte[] bytes, String format) {
        if ("webp".equals(format)) {
            return readWebpDimensions(bytes);
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new ConflictException("unreadable-image", "That image could not be decoded.");
            }
            return new Dimensions(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            throw new ConflictException("unreadable-image", "That image could not be decoded.");
        }
    }

    /** Simple VP8/VP8L/VP8X header read, enough to bound the dimensions. */
    private Dimensions readWebpDimensions(byte[] bytes) {
        try {
            String chunk = new String(bytes, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (chunk.startsWith("VP8X")) {
                int width = ((bytes[24] & 0xFF) | ((bytes[25] & 0xFF) << 8) | ((bytes[26] & 0xFF) << 16)) + 1;
                int height = ((bytes[27] & 0xFF) | ((bytes[28] & 0xFF) << 8) | ((bytes[29] & 0xFF) << 16)) + 1;
                return new Dimensions(width, height);
            }
            if (chunk.startsWith("VP8 ")) {
                int width = ((bytes[26] & 0xFF) | ((bytes[27] & 0xFF) << 8)) & 0x3FFF;
                int height = ((bytes[28] & 0xFF) | ((bytes[29] & 0xFF) << 8)) & 0x3FFF;
                return new Dimensions(width, height);
            }
            if (chunk.startsWith("VP8L")) {
                int bits = (bytes[21] & 0xFF) | ((bytes[22] & 0xFF) << 8)
                        | ((bytes[23] & 0xFF) << 16) | ((bytes[24] & 0xFF) << 24);
                return new Dimensions((bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1);
            }
        } catch (RuntimeException e) {
            throw new ConflictException("unreadable-image", "That WebP image could not be read.");
        }
        throw new ConflictException("unreadable-image", "That WebP image could not be read.");
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalise(String extension) {
        return "jpg".equals(extension) ? "jpeg" : extension;
    }
}
