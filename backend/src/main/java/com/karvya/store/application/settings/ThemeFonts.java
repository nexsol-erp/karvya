package com.karvya.store.application.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The typefaces an administrator may choose from.
 *
 * <p>A fixed list rather than a free-text field, for three reasons. The
 * Content-Security-Policy only permits fonts from Google's host, so an
 * arbitrary name would either fail to load or have to widen the policy. Each
 * entry carries a fallback stack, so the page still reads correctly in the
 * moment before the web font arrives, or if it never does. And a name that is
 * merely misspelt would otherwise fail silently, leaving the shop in a default
 * sans-serif with nothing to say why.
 *
 * <p>The fallback is chosen to match the shape of the web font - a serif falls
 * back to a serif - so the substitution during loading is not jarring.
 */
public final class ThemeFonts {

    /** Family name to the full CSS stack it should render as. */
    private static final Map<String, String> STACKS = new LinkedHashMap<>();

    static {
        // serif, for headings
        STACKS.put("Fraunces", "\"Fraunces\", \"Iowan Old Style\", Georgia, serif");
        STACKS.put("Playfair Display", "\"Playfair Display\", \"Iowan Old Style\", Georgia, serif");
        STACKS.put("Lora", "\"Lora\", \"Iowan Old Style\", Georgia, serif");
        STACKS.put("Cormorant Garamond", "\"Cormorant Garamond\", Garamond, Georgia, serif");
        STACKS.put("Libre Baskerville", "\"Libre Baskerville\", Baskerville, Georgia, serif");

        // sans-serif, for body text
        STACKS.put("Karla", "\"Karla\", \"Helvetica Neue\", Arial, sans-serif");
        STACKS.put("Inter", "\"Inter\", \"Helvetica Neue\", Arial, sans-serif");
        STACKS.put("Work Sans", "\"Work Sans\", \"Helvetica Neue\", Arial, sans-serif");
        STACKS.put("Nunito Sans", "\"Nunito Sans\", \"Helvetica Neue\", Arial, sans-serif");
        STACKS.put("Source Sans 3", "\"Source Sans 3\", \"Helvetica Neue\", Arial, sans-serif");
        STACKS.put("DM Sans", "\"DM Sans\", \"Helvetica Neue\", Arial, sans-serif");
    }

    private ThemeFonts() {
    }

    public static Set<String> names() {
        return STACKS.keySet();
    }

    public static boolean isAllowed(String family) {
        return family != null && STACKS.containsKey(family.trim());
    }
}
