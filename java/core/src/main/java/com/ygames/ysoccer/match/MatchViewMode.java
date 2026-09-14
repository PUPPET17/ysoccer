package com.ygames.ysoccer.match;

/**
 * Selects how the two-dimensional match world is presented to a local player.
 *
 * <p>The value is intentionally presentation-only: match rules, replay frames and network payloads
 * continue to use the original world coordinate system.</p>
 */
public enum MatchViewMode {
    /** Original view with the goals at the top and bottom of the screen. */
    VERTICAL("MATCH VIEW.VERTICAL"),
    /** Broadcast-style affine view with the goals at the left and right of the screen. */
    HORIZONTAL_2_5D("MATCH VIEW.HORIZONTAL 2.5D");

    private final String labelKey;

    MatchViewMode(String labelKey) {
        this.labelKey = labelKey;
    }

    /** Returns the translation key used by the match-options control. */
    public String getLabelKey() {
        return labelKey;
    }

    /**
     * Parses a saved preference without allowing an obsolete or damaged value to block startup.
     *
     * @param value persisted enum name, which may be null or unknown
     * @return the parsed mode, or {@link #VERTICAL} for legacy and invalid values
     */
    public static MatchViewMode fromPreference(String value) {
        if (value == null) {
            return VERTICAL;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            return VERTICAL;
        }
    }
}
