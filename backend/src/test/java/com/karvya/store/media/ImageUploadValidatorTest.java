package com.karvya.store.media;

import com.karvya.store.application.media.ImageUploadValidator;
import com.karvya.store.domain.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upload validation, tested as a unit because it needs no database and is the
 * one place where a hostile file is turned away.
 */
class ImageUploadValidatorTest {

    private final ImageUploadValidator validator = new ImageUploadValidator();

    /** A real encoded image, so the magic bytes and dimensions are genuine. */
    private byte[] realImage(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(168, 116, 63));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private MockMultipartFile upload(String filename, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", filename, contentType, bytes);
    }

    @Test
    @DisplayName("accepts a genuine PNG and reports its real dimensions")
    void acceptsRealPng() throws Exception {
        var result = validator.validate(upload("photo.png", "image/png", realImage("png", 800, 1000)));

        assertThat(result.extension()).isEqualTo("png");
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.width()).isEqualTo(800);
        assertThat(result.height()).isEqualTo(1000);
    }

    @Test
    @DisplayName("accepts a genuine JPEG under either extension")
    void acceptsRealJpeg() throws Exception {
        byte[] bytes = realImage("jpg", 600, 600);

        assertThat(validator.validate(upload("a.jpg", "image/jpeg", bytes)).extension()).isEqualTo("jpg");
        assertThat(validator.validate(upload("a.jpeg", "image/jpeg", bytes)).extension()).isEqualTo("jpg");
    }

    /**
     * The check that matters most: a script renamed to .png, with a declared
     * content type that also lies. Only the magic bytes tell the truth.
     */
    @Test
    @DisplayName("refuses a script wearing an image extension and content type")
    void refusesDisguisedScript() {
        byte[] payload = "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> validator.validate(upload("innocent.png", "image/png", payload)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not a JPEG, PNG or WebP");
    }

    @Test
    @DisplayName("refuses a real PNG renamed to .jpg")
    void refusesExtensionThatDisagreesWithContent() throws Exception {
        byte[] png = realImage("png", 500, 500);

        assertThatThrownBy(() -> validator.validate(upload("actually-a-png.jpg", "image/jpeg", png)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    @DisplayName("refuses an extension that is not an image at all")
    void refusesUnsupportedExtension() throws Exception {
        assertThatThrownBy(() ->
                validator.validate(upload("payload.svg", "image/svg+xml", realImage("png", 400, 400))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("JPEG, PNG or WebP");
    }

    @Test
    @DisplayName("refuses an empty upload")
    void refusesEmptyUpload() {
        assertThatThrownBy(() -> validator.validate(upload("nothing.png", "image/png", new byte[0])))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No file");
    }

    @Test
    @DisplayName("refuses a file beyond the size limit")
    void refusesOversizedFile() {
        byte[] tooBig = new byte[9 * 1024 * 1024];
        // valid PNG signature, so it is the size check that rejects it
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, tooBig, 0, 8);

        assertThatThrownBy(() -> validator.validate(upload("huge.png", "image/png", tooBig)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("larger than");
    }

    @Test
    @DisplayName("refuses an image too small to be a product photograph")
    void refusesTinyImage() throws Exception {
        assertThatThrownBy(() ->
                validator.validate(upload("icon.png", "image/png", realImage("png", 32, 32))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("at least");
    }

    @Test
    @DisplayName("refuses an image with absurd dimensions")
    void refusesOversizedDimensions() throws Exception {
        // 7000px on a side: small as a flat-colour PNG, enormous once decoded
        assertThatThrownBy(() ->
                validator.validate(upload("bomb.png", "image/png", realImage("png", 7000, 300))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("pixels on a side");
    }

    @Test
    @DisplayName("a declared content type is never trusted on its own")
    void ignoresDeclaredContentType() throws Exception {
        // the client claims PDF; the bytes are a real PNG and the name says png
        var result = validator.validate(
                upload("photo.png", "application/pdf", realImage("png", 400, 400)));

        assertThat(result.contentType())
                .as("the stored type comes from the bytes, not the client")
                .isEqualTo("image/png");
    }
}
