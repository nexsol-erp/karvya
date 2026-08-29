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
    FONT,
    /**
     * A credential. Never returned by the API - the admin screen is told only
     * whether one is stored - and an empty submission leaves it unchanged
     * rather than clearing it.
     */
    SECRET
}
