package com.karvya.store.application.admin;

import com.karvya.store.application.settings.SettingsService;
import com.karvya.store.domain.ConflictException;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.SettingType;
import com.karvya.store.domain.model.SiteSetting;
import com.karvya.store.domain.repository.SiteSettingRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Reading and changing site settings.
 *
 * <p>Every value is validated against its declared type before it is stored,
 * and rich text is sanitised. Settings are rendered into pages customers see,
 * so an administrator account - or anyone who takes one over - must not be
 * able to turn a policy page into a script delivery mechanism.
 */
@Service
public class AdminSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AdminSettingsService.class);

    /**
     * {@code placeholder} and {@code unset} are separate states, not one.
     * Seeded copy still saying [PLACEHOLDER] is visibly wrong to a customer;
     * an empty WhatsApp number silently removes a feature instead. Both need
     * attention before launch, but they are different problems and the screen
     * should say which.
     */
    public record SettingView(
            String key,
            String value,
            SettingType valueType,
            String description,
            boolean placeholder,
            boolean unset
    ) {
        static SettingView from(SiteSetting setting) {
            return new SettingView(setting.getKey(), setting.getValue(), setting.getValueType(),
                    setting.getDescription(), setting.isPlaceholder(),
                    setting.getValue() == null || setting.getValue().isBlank());
        }
    }

    public record Update(
            @NotNull(message = "Send the settings to change")
            @Size(max = 200)
            Map<String, String> values
    ) {
    }

    /**
     * What rich-text settings may contain.
     *
     * <p>Formatting and links, nothing that executes. jsoup's basicWithImages
     * excludes script, style, iframe, object and every on* handler; the extra
     * protocol rule keeps {@code javascript:} out of hrefs.
     */
    private static final Safelist RICH_TEXT = Safelist.basicWithImages()
            .addAttributes("a", "href", "title", "rel")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https");

    private final SiteSettingRepository repository;
    private final SettingsService settings;

    public AdminSettingsService(SiteSettingRepository repository, SettingsService settings) {
        this.repository = repository;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public List<SettingView> listAll() {
        return repository.findAllByOrderByKeyAsc().stream().map(SettingView::from).toList();
    }

    /**
     * Applies a batch of changes.
     *
     * <p>Everything is validated first and written second, so a form with one
     * bad field leaves nothing half-applied.
     */
    @Transactional
    public List<SettingView> update(Map<String, String> values, String actor) {
        Map<String, SiteSetting> known = repository.findAllByOrderByKeyAsc().stream()
                .collect(java.util.stream.Collectors.toMap(SiteSetting::getKey, s -> s));

        // validate the whole batch before touching anything
        Map<String, String> cleaned = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            SiteSetting setting = known.get(entry.getKey());
            if (setting == null) {
                // an unknown key is a client bug, not something to create
                throw new NotFoundException("Setting", entry.getKey());
            }
            cleaned.put(entry.getKey(), coerce(setting, entry.getValue()));
        }

        cleaned.forEach((key, value) -> known.get(key).setValue(value, actor));
        repository.flush();
        settings.reload();

        log.info("{} updated {} settings", actor, cleaned.size());
        return listAll();
    }

    /** Validates and normalises one value against its declared type. */
    private String coerce(SiteSetting setting, String raw) {
        String value = (raw == null || raw.isBlank()) ? null : raw.trim();
        if (value == null) {
            return null;
        }

        return switch (setting.getValueType()) {
            case INTEGER -> {
                try {
                    yield String.valueOf(Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    throw invalid(setting, "a whole number");
                }
            }
            case DECIMAL -> {
                try {
                    BigDecimal parsed = new BigDecimal(value);
                    if (parsed.signum() < 0) {
                        throw invalid(setting, "an amount of zero or more");
                    }
                    yield parsed.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
                } catch (NumberFormatException e) {
                    throw invalid(setting, "an amount such as 80.00");
                }
            }
            case BOOLEAN -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw invalid(setting, "true or false");
                }
                yield value.toLowerCase();
            }
            case URL -> {
                try {
                    URI uri = URI.create(value);
                    if (uri.getScheme() == null
                            || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
                        throw invalid(setting, "a link beginning http:// or https://");
                    }
                    yield value;
                } catch (IllegalArgumentException e) {
                    throw invalid(setting, "a valid link");
                }
            }
            // sanitised, not escaped: the point is to keep the formatting and
            // drop anything that could run
            case HTML -> Jsoup.clean(value, RICH_TEXT);
            case JSON -> value;
            case STRING, TEXT -> Jsoup.clean(value, Safelist.none());
        };
    }

    private ConflictException invalid(SiteSetting setting, String expected) {
        return new ConflictException("invalid-setting-value",
                "'" + setting.getKey() + "' needs " + expected + ".");
    }
}
