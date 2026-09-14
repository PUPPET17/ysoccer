package com.ygames.ysoccer.match;

import com.ygames.ysoccer.framework.GLGame;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Match-local coach intent for one team.
 *
 * <p>This state supplies bounded biases to the existing positioning and decision code. Current
 * team values approach requested values over time so a tactical change moves players through
 * their normal running states instead of changing coordinates. Individual instructions are
 * keyed by the actual {@link Player} object so substitutions, shirt-number lookup, and lineup
 * reordering cannot accidentally transfer an instruction to another footballer.</p>
 *
 * <p>The class is deliberately independent from speech, text parsing, input devices, and network
 * transport. Those systems may request values later, but only match AI is allowed to interpret
 * their football meaning.</p>
 */
public final class TacticalState {

    public static final float DEFAULT_WIDTH = 1.0f;
    public static final float DEFAULT_COMPACTNESS = 1.0f;
    public static final float DEFAULT_TEMPO = 1.0f;
    public static final float DEFAULT_PASSING_RISK = 1.0f;
    public static final float DEFAULT_FORWARD_RUN_RATE = 1.0f;
    public static final float DEFAULT_BALL_RETENTION = 1.0f;
    public static final float DEFAULT_BACK_PASS_PREFERENCE = 1.0f;

    /** Maximum per-second movement of a team-level intent value toward its requested value. */
    private static final float TRANSITION_PER_SECOND = 0.30f;
    private static final float TRANSITION_STEP = TRANSITION_PER_SECOND / GLGame.VIRTUAL_REFRESH_RATE;

    /** Current lateral scale around the formation centre; values below one make the team narrower. */
    private float width;
    private float targetWidth;
    /** Current inverse vertical-spacing scale; values above one reduce distances between lines. */
    private float compactness;
    private float targetCompactness;
    /** Current decision cadence bias; it never changes physical player speed. */
    private float tempo;
    private float targetTempo;
    /** Current preference for progressive/riskier passing options. */
    private float passingRisk;
    private float targetPassingRisk;
    /** Current scale applied to advanced support targets while this team has possession. */
    private float forwardRunRate;
    private float targetForwardRunRate;
    /** Current preference for retaining the ball and using nearby support. */
    private float ballRetention;
    private float targetBallRetention;
    /** Current relative preference for passes toward the team's own goal. */
    private float backPassPreference;
    private float targetBackPassPreference;

    /** Per-footballer overrides that augment, rather than replace, the team profile. */
    private final Map<Player, PlayerTacticalInstruction> playerInstructions = new IdentityHashMap<>();

    public TacticalState() {
        resetImmediately();
    }

    /**
     * Requests a new team shape. The current values converge gradually during AI updates.
     *
     * @param width lateral scale in the supported range 0.5..1.5
     * @param compactness inverse line-spacing scale in the supported range 0.67..1.5
     */
    public void setShape(float width, float compactness) {
        targetWidth = clamp(width, 0.5f, 1.5f);
        targetCompactness = clamp(compactness, 0.67f, 1.5f);
    }

    /** Requests a gradual return to the formation's unmodified width and line spacing. */
    public void resetShape() {
        setShape(DEFAULT_WIDTH, DEFAULT_COMPACTNESS);
    }

    /**
     * Requests the team decision profile used by possession and support-running AI.
     * None of these values changes the physical speed of a player or the ball.
     *
     * @param tempo decision cadence bias in the range 0.5..1.5
     * @param passingRisk progressive-pass preference in the range 0.25..1.75
     * @param forwardRunRate advanced support-run scale in the range 0.25..1.5
     * @param ballRetention possession-retention preference in the range 0.75..1.75
     * @param backPassPreference back-pass preference in the range 0.5..2.0
     */
    public void setPossessionProfile(float tempo, float passingRisk, float forwardRunRate,
                                     float ballRetention, float backPassPreference) {
        targetTempo = clamp(tempo, 0.5f, 1.5f);
        targetPassingRisk = clamp(passingRisk, 0.25f, 1.75f);
        targetForwardRunRate = clamp(forwardRunRate, 0.25f, 1.5f);
        targetBallRetention = clamp(ballRetention, 0.75f, 1.75f);
        targetBackPassPreference = clamp(backPassPreference, 0.5f, 2.0f);
    }

    /** Requests a gradual return to the default possession decision profile. */
    public void resetPossessionProfile() {
        setPossessionProfile(
            DEFAULT_TEMPO,
            DEFAULT_PASSING_RISK,
            DEFAULT_FORWARD_RUN_RATE,
            DEFAULT_BALL_RETENTION,
            DEFAULT_BACK_PASS_PREFERENCE
        );
    }

    /**
     * Adds or strengthens an instruction for one footballer without weakening an existing one.
     *
     * @param player player identity that owns the instruction for this match
     * @param instruction normalized preferences to merge with the player's current profile
     */
    public void addPlayerInstruction(Player player, PlayerTacticalInstruction instruction) {
        if (player == null || instruction == null || instruction.isDefault()) return;
        PlayerTacticalInstruction current = playerInstructions.get(player);
        playerInstructions.put(player, current == null ? instruction : current.merge(instruction));
    }

    /** Returns the instruction for a player, or the neutral profile when no override exists. */
    public PlayerTacticalInstruction getInstruction(Player player) {
        PlayerTacticalInstruction instruction = playerInstructions.get(player);
        return instruction == null ? PlayerTacticalInstruction.defaults() : instruction;
    }

    /** Removes all player-specific coach preferences while preserving the team profile. */
    public void clearPlayerInstructions() {
        playerInstructions.clear();
    }

    /**
     * Requests default team values and immediately removes player-specific preferences.
     * Team-level values still converge gradually, so restoring defaults does not jump targets.
     */
    public void resetAll() {
        resetShape();
        resetPossessionProfile();
        clearPlayerInstructions();
    }

    /** Advances current coach intent by one 64 Hz AI frame. */
    void update() {
        width = approach(width, targetWidth);
        compactness = approach(compactness, targetCompactness);
        tempo = approach(tempo, targetTempo);
        passingRisk = approach(passingRisk, targetPassingRisk);
        forwardRunRate = approach(forwardRunRate, targetForwardRunRate);
        ballRetention = approach(ballRetention, targetBallRetention);
        backPassPreference = approach(backPassPreference, targetBackPassPreference);
    }

    /** Resets all current and requested values for a new match without carrying old instructions. */
    void resetImmediately() {
        width = targetWidth = DEFAULT_WIDTH;
        compactness = targetCompactness = DEFAULT_COMPACTNESS;
        tempo = targetTempo = DEFAULT_TEMPO;
        passingRisk = targetPassingRisk = DEFAULT_PASSING_RISK;
        forwardRunRate = targetForwardRunRate = DEFAULT_FORWARD_RUN_RATE;
        ballRetention = targetBallRetention = DEFAULT_BALL_RETENTION;
        backPassPreference = targetBackPassPreference = DEFAULT_BACK_PASS_PREFERENCE;
        playerInstructions.clear();
    }

    public float getWidth() {
        return width;
    }

    public float getTargetWidth() {
        return targetWidth;
    }

    public float getCompactness() {
        return compactness;
    }

    public float getTargetCompactness() {
        return targetCompactness;
    }

    public float getTempo() {
        return tempo;
    }

    public float getPassingRisk() {
        return passingRisk;
    }

    public float getForwardRunRate() {
        return forwardRunRate;
    }

    public float getBallRetention() {
        return ballRetention;
    }

    public float getBackPassPreference() {
        return backPassPreference;
    }

    /** Returns whether AI passing should use the new direction-aware option scorer. */
    boolean usesTacticalPassing() {
        return !approximately(passingRisk, DEFAULT_PASSING_RISK)
            || !approximately(ballRetention, DEFAULT_BALL_RETENTION)
            || !approximately(backPassPreference, DEFAULT_BACK_PASS_PREFERENCE);
    }

    private float approach(float current, float target) {
        if (current < target) return Math.min(target, current + TRANSITION_STEP);
        if (current > target) return Math.max(target, current - TRANSITION_STEP);
        return current;
    }

    private boolean approximately(float a, float b) {
        return Math.abs(a - b) < 0.0001f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
