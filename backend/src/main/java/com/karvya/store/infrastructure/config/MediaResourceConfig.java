package com.karvya.store.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Serves product imagery from the configured storage directory.
 *
 * <p>In production Nginx serves this path directly and never reaches the
 * application. This handler exists so that local development works without a
 * reverse proxy in front, and so integration tests can assert on real files.
 *
 * <p>Derivatives are content-addressed by width and format and are replaced by
 * a new key rather than overwritten, so they cache for a year safely.
 */
@Configuration
public class MediaResourceConfig implements WebMvcConfigurer {

    private final Path storageDir;

    public MediaResourceConfig(@Value("${app.storage-dir}") String storageDir) {
        this.storageDir = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**")
                .addResourceLocations(storageDir.toUri().toString())
                .setCachePeriod((int) Duration.ofDays(365).toSeconds());
    }
}
