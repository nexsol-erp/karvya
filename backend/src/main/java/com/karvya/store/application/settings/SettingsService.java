package com.karvya.store.application.settings;

import com.karvya.store.domain.model.SiteSetting;
import com.karvya.store.domain.repository.SiteSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Typed access to site settings.
 *
 * <p>Settings are read on nearly every request - the delivery charge is needed
 * to price any cart - so the whole table is held in an immutable snapshot and
 * replaced wholesale on write. The map is small and changes rarely; a cache
 * that can be stale for one request would be worse than one that is simply
 * swapped atomically.
 *
 * <p>Every accessor takes a fallback. A missing or unparseable value must not
 * be able to stop a customer checking out, so it degrades to the default and
 * says so in the log rather than throwing.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    public static final String STORE_NAME = "store.name";
    public static final String STORE_TAGLINE = "store.tagline";
    public static final String LOGO_KEY = "store.logo_key";
    public static final String WHATSAPP_NUMBER = "contact.whatsapp_number";
    public static final String ADMIN_EMAIL = "contact.admin_email";
    public static final String PUBLIC_EMAIL = "contact.public_email";
    public static final String BUSINESS_ADDRESS = "contact.address";
    public static final String CURRENCY = "locale.currency";
    public static final String LOCALE_TAG = "locale.tag";
    public static final String DELIVERY_CHARGE = "delivery.charge";
    public static final String FREE_DELIVERY_THRESHOLD = "delivery.free_threshold";
    public static final String LOW_STOCK_THRESHOLD = "catalogue.low_stock_threshold";
    public static final String CHECKOUT_NOTICE = "content.checkout_notice";

    public static final String COLOUR_PRIMARY = "theme.colour_primary";
    public static final String COLOUR_SECONDARY = "theme.colour_secondary";
    public static final String COLOUR_BACKGROUND = "theme.colour_background";
    public static final String COLOUR_SURFACE = "theme.colour_surface";
    public static final String COLOUR_TEXT = "theme.colour_text";
    public static final String FONT_HEADING = "theme.font_heading";
    public static final String FONT_BODY = "theme.font_body";
    public static final String CORNER_RADIUS = "theme.corner_radius";

    public static final String MAIL_HOST = "mail.host";
    public static final String MAIL_PORT = "mail.port";
    public static final String MAIL_USERNAME = "mail.username";
    public static final String MAIL_PASSWORD = "mail.password";
    public static final String MAIL_FROM = "mail.from";
    public static final String MAIL_AUTH = "mail.auth";
    public static final String MAIL_STARTTLS = "mail.starttls";

    private final SiteSettingRepository repository;
    private final AtomicReference<Map<String, String>> cache = new AtomicReference<>(Map.of());

    public SettingsService(SiteSettingRepository repository) {
        this.repository = repository;
    }

    /** Reloads the snapshot. Called on write and lazily on first read. */
    @Transactional(readOnly = true)
    public void reload() {
        Map<String, String> loaded = new HashMap<>();
        for (SiteSetting setting : repository.findAll()) {
            if (setting.getValue() != null) {
                loaded.put(setting.getKey(), setting.getValue());
            }
        }
        cache.set(Map.copyOf(loaded));
    }

    private Map<String, String> snapshot() {
        Map<String, String> current = cache.get();
        if (current.isEmpty()) {
            reload();
            current = cache.get();
        }
        return current;
    }

    public Optional<String> find(String key) {
        String value = snapshot().get(key);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
    }

    public String getString(String key, String fallback) {
        return find(key).orElse(fallback);
    }

    public BigDecimal getMoney(String key, BigDecimal fallback) {
        return find(key).map(raw -> {
            try {
                BigDecimal parsed = new BigDecimal(raw.trim());
                return parsed.signum() < 0 ? fallback : parsed;
            } catch (NumberFormatException e) {
                log.warn("Setting {} is not a valid amount ({}); using {}", key, raw, fallback);
                return fallback;
            }
        }).orElse(fallback);
    }

    public int getInt(String key, int fallback) {
        return find(key).map(raw -> {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                log.warn("Setting {} is not a valid integer ({}); using {}", key, raw, fallback);
                return fallback;
            }
        }).orElse(fallback);
    }

    /** Empty means the rule is switched off, which is not the same as zero. */
    /** Anything other than a stored "true" is false, including an unset key. */
    public boolean getBoolean(String key, boolean fallback) {
        return find(key).map(Boolean::parseBoolean).orElse(fallback);
    }

    public Optional<BigDecimal> getOptionalMoney(String key) {
        return find(key).flatMap(raw -> {
            try {
                return Optional.of(new BigDecimal(raw.trim()));
            } catch (NumberFormatException e) {
                log.warn("Setting {} is not a valid amount ({}); ignoring", key, raw);
                return Optional.empty();
            }
        });
    }

    @Transactional
    public void put(String key, String value, String updatedBy) {
        repository.findById(key).ifPresent(setting -> setting.setValue(value, updatedBy));
        repository.flush();
        reload();
    }
}
