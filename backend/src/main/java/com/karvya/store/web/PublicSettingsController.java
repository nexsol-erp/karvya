package com.karvya.store.web;

import com.karvya.store.application.settings.SettingsService;
import com.karvya.store.infrastructure.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The settings the storefront needs, served at runtime.
 *
 * <p>This exists because the alternative does not work: Vite inlines
 * {@code VITE_*} variables at build time, so a WhatsApp number configured
 * through the admin would be ignored in favour of whatever was baked into the
 * bundle. Anything an administrator can change has to be fetched, not compiled.
 *
 * <p>Only what a public page renders. Administrator email, SMTP details and
 * every internal key stay out - this endpoint is unauthenticated.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Settings", description = "Public storefront configuration")
public class PublicSettingsController {

    /** Keys that may be read without authentication. Nothing else is exposed. */
    private static final Map<String, String> PUBLIC_KEYS = Map.ofEntries(
            Map.entry(SettingsService.STORE_NAME, "storeName"),
            Map.entry(SettingsService.STORE_TAGLINE, "tagline"),
            Map.entry(SettingsService.WHATSAPP_NUMBER, "whatsAppNumber"),
            Map.entry(SettingsService.PUBLIC_EMAIL, "contactEmail"),
            Map.entry(SettingsService.BUSINESS_ADDRESS, "businessAddress"),
            Map.entry(SettingsService.CURRENCY, "currency"),
            Map.entry(SettingsService.LOCALE_TAG, "locale"),
            Map.entry(SettingsService.CHECKOUT_NOTICE, "checkoutNotice"),
            Map.entry("content.hero_heading", "heroHeading"),
            Map.entry("content.hero_subheading", "heroSubheading"),
            Map.entry("content.story_heading", "storyHeading"),
            Map.entry("content.story_body", "storyBody"),
            Map.entry("content.why_handmade_body", "whyHandmadeBody"),
            Map.entry("content.materials_body", "materialsBody"),
            Map.entry("social.instagram", "instagram"),
            Map.entry("social.facebook", "facebook"),
            Map.entry("social.youtube", "youtube"),
            Map.entry("policy.shipping", "shippingPolicy"),
            Map.entry("policy.returns", "returnsPolicy"),
            Map.entry("policy.privacy", "privacyPolicy"),
            // appearance: the browser needs these to paint the first screen,
            // and none of it is a secret
            Map.entry(SettingsService.COLOUR_PRIMARY, "colourPrimary"),
            Map.entry(SettingsService.COLOUR_SECONDARY, "colourSecondary"),
            Map.entry(SettingsService.COLOUR_BACKGROUND, "colourBackground"),
            Map.entry(SettingsService.COLOUR_SURFACE, "colourSurface"),
            Map.entry(SettingsService.COLOUR_TEXT, "colourText"),
            Map.entry(SettingsService.FONT_HEADING, "fontHeading"),
            Map.entry(SettingsService.FONT_BODY, "fontBody"),
            Map.entry(SettingsService.CORNER_RADIUS, "cornerRadius"));

    private final SettingsService settings;
    private final AppProperties properties;

    public PublicSettingsController(SettingsService settings, AppProperties properties) {
        this.settings = settings;
        this.properties = properties;
    }

    @GetMapping("/settings/public")
    @Operation(summary = "Storefront configuration an administrator can change")
    public ResponseEntity<Map<String, Object>> publicSettings() {
        Map<String, Object> body = new LinkedHashMap<>();

        PUBLIC_KEYS.forEach((key, name) -> body.put(name, settings.find(key).orElse(null)));

        // the WhatsApp number falls back to the environment, so a deployment
        // that has not been configured through the admin still works
        if (body.get("whatsAppNumber") == null) {
            body.put("whatsAppNumber", blankToNull(properties.whatsAppNumber()));
        }

        // normalised here rather than in each client: a number typed as
        // +91 97468 00113 or 0091 97468 00113 must reach wa.me as 919746800113
        body.put("whatsAppNumber", normaliseMsisdn((String) body.get("whatsAppNumber")));

        body.put("deliveryCharge", settings.getMoney(SettingsService.DELIVERY_CHARGE, BigDecimal.ZERO));
        body.put("freeDeliveryThreshold",
                settings.getOptionalMoney(SettingsService.FREE_DELIVERY_THRESHOLD).orElse(null));

        // Short cache. Long enough to spare the database on a busy page, short
        // enough that an administrator sees their change without wondering
        // whether it saved.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(body);
    }

    /**
     * Reduces a dialled number to the digits wa.me expects: country code first,
     * no plus, no international prefix, no separators.
     *
     * <p>People write the same number as {@code +91 97468 00113},
     * {@code 0091-9746800113} or {@code 00919746800113}. Only the last form is
     * ambiguous with a leading zero, and stripping {@code 00} is safe because
     * no country code begins with it.
     */
    static String normaliseMsisdn(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        return digits.isBlank() ? null : digits;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
