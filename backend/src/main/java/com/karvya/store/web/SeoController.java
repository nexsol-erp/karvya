package com.karvya.store.web;

import com.karvya.store.domain.model.ProductStatus;
import com.karvya.store.domain.repository.CategoryRepository;
import com.karvya.store.domain.repository.ProductRepository;
import com.karvya.store.domain.repository.ProductSpecifications;
import com.karvya.store.infrastructure.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * The two files crawlers ask for.
 *
 * <p>Generated from live data rather than kept as static files, so a product
 * that is archived stops being advertised the moment it is archived - a stale
 * sitemap pointing at 404s is worse than none.
 */
@RestController
@Tag(name = "SEO", description = "Sitemap and robots directives")
public class SeoController {

    /** Enough for a catalogue of this size; a larger one would need an index. */
    private static final int MAX_URLS = 2000;

    private static final DateTimeFormatter W3C_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final AppProperties properties;

    public SeoController(ProductRepository products, CategoryRepository categories,
                         AppProperties properties) {
        this.products = products;
        this.categories = categories;
        this.properties = properties;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Transactional(readOnly = true)
    @Operation(summary = "Sitemap generated from the live catalogue")
    public ResponseEntity<String> sitemap() {
        String base = trimTrailingSlash(properties.baseUrl());
        StringBuilder xml = new StringBuilder(4096);

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // the pages that always exist
        url(xml, base + "/", null, "daily", "1.0");
        url(xml, base + "/shop", null, "daily", "0.9");
        url(xml, base + "/our-story", null, "monthly", "0.5");
        url(xml, base + "/contact", null, "monthly", "0.5");

        categories.findActiveWithProductCounts().forEach(category ->
                url(xml, base + "/shop/" + category.slug(), null, "weekly", "0.7"));

        products.findAll(
                        ProductSpecifications.any()
                                .and(ProductSpecifications.hasStatus(ProductStatus.ACTIVE)),
                        PageRequest.of(0, MAX_URLS, Sort.by(Sort.Order.desc("updatedAt"))))
                .forEach(product -> url(xml,
                        base + "/product/" + product.getSlug(),
                        product.getUpdatedAt() == null ? null : W3C_DATE.format(product.getUpdatedAt()),
                        "weekly", "0.8"));

        xml.append("</urlset>\n");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml.toString());
    }

    /**
     * Robots directives.
     *
     * <p>Cart, checkout, order confirmations, the account area and the whole
     * back office are excluded. None of them are useful in a search result and
     * several carry a token or personal detail in the URL.
     */
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Crawler directives")
    public ResponseEntity<String> robots() {
        String base = trimTrailingSlash(properties.baseUrl());

        String body = """
                User-agent: *
                Allow: /
                Disallow: /admin
                Disallow: /account
                Disallow: /cart
                Disallow: /checkout
                Disallow: /order/
                Disallow: /login
                Disallow: /register
                Disallow: /forgot-password
                Disallow: /reset-password
                Disallow: /api/

                Sitemap: %s/sitemap.xml
                """.formatted(base);

        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(body);
    }

    private void url(StringBuilder xml, String location, String lastModified,
                     String changeFrequency, String priority) {
        xml.append("  <url>\n")
                .append("    <loc>").append(escape(location)).append("</loc>\n");
        if (lastModified != null) {
            xml.append("    <lastmod>").append(lastModified).append("</lastmod>\n");
        }
        xml.append("    <changefreq>").append(changeFrequency).append("</changefreq>\n")
                .append("    <priority>").append(priority).append("</priority>\n")
                .append("  </url>\n");
    }

    /** Slugs are constrained to a safe alphabet, but the base URL is configured. */
    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
