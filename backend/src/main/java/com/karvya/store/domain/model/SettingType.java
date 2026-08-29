package com.karvya.store.domain.model;

/** How a setting's stored text should be parsed and edited. */
public enum SettingType {
    STRING,
    TEXT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    URL,
    HTML,
    JSON,
    /** A #RRGGBB colour. */
    COLOUR,
    /** A typeface name from the permitted list. */
    FONT
}
