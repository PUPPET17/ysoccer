package com.ygames.ysoccer.match;

/**
 * Immutable coach preferences that bias one player's normal match decisions.
 *
 * <p>The values are normalized biases rather than commands. A zero value preserves the
 * player's existing AI. Positive defensive values increase defensive involvement, while
 * the attacking values describe how strongly the player should offer short, hold the ball,
 * run beyond the defence, or lay the ball off. No value grants permission to move a player
 * directly or bypass the normal player and AI state machines.</p>
 */
public final class PlayerTacticalInstruction {

    private static final PlayerTacticalInstruction DEFAULT = new PlayerTacticalInstruction(
        0, 0, 0, 0, 0, 0, 0, 0
    );

    /** Preference for recovering into the defensive team shape when possession is lost. */
    private final float defensiveWorkRate;
    /** Preference for tracking play back toward the player's own goal. */
    private final float trackingBack;
    /** Goal-side depth bias applied while the opponent has possession. */
    private final float defensiveDepth;
    /** Preference for becoming a nearby secondary defender without forcing primary pressure. */
    private final float pressSupport;
    /** Preference for moving toward midfield to offer a shorter passing option. */
    private final float comeShort;
    /** Preference for protecting possession and using nearby teammates instead of turning directly at goal. */
    private final float holdUpPlay;
    /** Forward-run bias; negative values reduce runs beyond the defence. */
    private final float runBehind;
    /** Preference for an early short layoff after receiving the ball. */
    private final float layoffPreference;

    /**
     * Creates a complete per-player instruction profile.
     *
     * @param defensiveWorkRate defensive recovery bias in the range 0..1
     * @param trackingBack tracking-back bias in the range 0..1
     * @param defensiveDepth additional goal-side depth bias in the range 0..1
     * @param pressSupport secondary pressure-support bias in the range 0..1
     * @param comeShort short-support bias in the range 0..1
     * @param holdUpPlay hold-up-play bias in the range 0..1
     * @param runBehind runs-behind bias in the range -1..1
     * @param layoffPreference short-layoff bias in the range 0..1
     */
    public PlayerTacticalInstruction(float defensiveWorkRate, float trackingBack,
                                     float defensiveDepth, float pressSupport,
                                     float comeShort, float holdUpPlay,
                                     float runBehind, float layoffPreference) {
        this.defensiveWorkRate = clamp01(defensiveWorkRate);
        this.trackingBack = clamp01(trackingBack);
        this.defensiveDepth = clamp01(defensiveDepth);
        this.pressSupport = clamp01(pressSupport);
        this.comeShort = clamp01(comeShort);
        this.holdUpPlay = clamp01(holdUpPlay);
        this.runBehind = clamp(runBehind, -1, 1);
        this.layoffPreference = clamp01(layoffPreference);
    }

    /** Returns the neutral profile, which leaves the existing player AI unchanged. */
    public static PlayerTacticalInstruction defaults() {
        return DEFAULT;
    }

    /** Returns the first-demo target-forward profile. */
    public static PlayerTacticalInstruction holdUpForward() {
        return new PlayerTacticalInstruction(0, 0, 0, 0, 0.8f, 1.0f, -0.7f, 1.0f);
    }

    /** Returns the first-demo individual defensive-work profile. */
    public static PlayerTacticalInstruction defendMore() {
        return new PlayerTacticalInstruction(1.0f, 1.0f, 0.85f, 0.8f, 0, 0, 0, 0);
    }

    /**
     * Combines independent coach preferences without discarding an instruction already assigned
     * to the same player. Positive preferences take the stronger value; a negative run-behind
     * preference remains the more conservative of the two profiles.
     *
     * @param other additional instruction to merge
     * @return a new profile containing both sets of preferences
     */
    public PlayerTacticalInstruction merge(PlayerTacticalInstruction other) {
        if (other == null) return this;
        return new PlayerTacticalInstruction(
            Math.max(defensiveWorkRate, other.defensiveWorkRate),
            Math.max(trackingBack, other.trackingBack),
            Math.max(defensiveDepth, other.defensiveDepth),
            Math.max(pressSupport, other.pressSupport),
            Math.max(comeShort, other.comeShort),
            Math.max(holdUpPlay, other.holdUpPlay),
            Math.abs(runBehind) >= Math.abs(other.runBehind) ? runBehind : other.runBehind,
            Math.max(layoffPreference, other.layoffPreference)
        );
    }

    public float getDefensiveWorkRate() {
        return defensiveWorkRate;
    }

    public float getTrackingBack() {
        return trackingBack;
    }

    public float getDefensiveDepth() {
        return defensiveDepth;
    }

    public float getPressSupport() {
        return pressSupport;
    }

    public float getComeShort() {
        return comeShort;
    }

    public float getHoldUpPlay() {
        return holdUpPlay;
    }

    public float getRunBehind() {
        return runBehind;
    }

    public float getLayoffPreference() {
        return layoffPreference;
    }

    /** Returns whether this profile has no effect on the existing AI. */
    public boolean isDefault() {
        return defensiveWorkRate == 0 && trackingBack == 0 && defensiveDepth == 0
            && pressSupport == 0 && comeShort == 0 && holdUpPlay == 0
            && runBehind == 0 && layoffPreference == 0;
    }

    private static float clamp01(float value) {
        return clamp(value, 0, 1);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
