package com.karvya.store.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One configurable value.
 *
 * <p>A typed key/value table rather than a settings entity with a column per
 * option: adding a setting is then a data change, not a migration, and the
 * admin screen can render an editor from {@code valueType} without knowing
 * what the key means.
 */
@Entity
@Table(name = "site_setting")
public class SiteSetting {

    @Id
    @Column(name = "setting_key", length = 96)
    private String key;

    @Column(name = "setting_value", columnDefinition = "text")
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    private SettingType valueType = SettingType.STRING;

    @Column(length = 400)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 160)
    private String updatedBy;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    protected SiteSetting() {
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public SettingType getValueType() { return valueType; }
    public String getDescription() { return description; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }

    public void setValue(String value, String updatedBy) {
        this.value = value;
        this.updatedBy = updatedBy;
    }

    /** True when the seeded copy has not yet been replaced by the business. */
    public boolean isPlaceholder() {
        return value != null && value.contains("[PLACEHOLDER]");
    }
}
