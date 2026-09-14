package com.ygames.ysoccer.match;

/**
 * Converts a formation target into a biased target while preserving normal player movement.
 *
 * <p>The calculator never reads or writes a player's actual coordinates. It only modifies the
 * target consumed by {@link AiStatePositioning}; the player's normal input, speed, animation,
 * collision, and state-machine rules remain responsible for reaching it.</p>
 */
final class TacticalPositioning {

    private static final float COME_SHORT_DISTANCE = 75f;
    private static final float RUN_BEHIND_DISTANCE = 65f;
    private static final float DEFENSIVE_DEPTH_DISTANCE = 70f;
    private static final float TRACKING_BACK_DISTANCE = 55f;
    private static final float PRESS_SUPPORT_X_BLEND = 0.22f;
    private static final float PRESS_SUPPORT_Y_BLEND = 0.18f;
    private static final float PRESS_SUPPORT_GOAL_SIDE_DISTANCE = 90f;
    private static final float FLANK_THRESHOLD = 80f;
    private static final float TRANSITION_POSSESSION_INFLUENCE = 0.20f;
    private static final float TRANSITION_HOLD_UP_INFLUENCE = 0.55f;
    private static final float DEFENSIVE_TRANSITION_RECOVERY_INFLUENCE = 1.20f;
    private static final float STABLE_DEFENSE_PRESS_INFLUENCE = 0.65f;

    private TacticalPositioning() {
    }

    /** Returns the target's horizontal coordinate after shape and same-flank support biases. */
    static float targetX(float baseX, float centreX, float ballX, TeamPhase phase,
                          TacticalState state, PlayerTacticalInstruction instruction) {
        float target = centreX + (baseX - centreX) * state.getWidth();
        float pressInfluence = defensivePressInfluence(phase);
        if (pressInfluence > 0 && sameFlank(target, ballX)) {
            float blend = PRESS_SUPPORT_X_BLEND
                * instruction.getPressSupport()
                * instruction.getDefensiveWorkRate()
                * pressInfluence;
            target += (ballX - target) * blend;
        }
        return clamp(target, -Const.TOUCH_LINE + 20, Const.TOUCH_LINE - 20);
    }

    /**
     * Returns the target's vertical coordinate after line spacing, possession support, and
     * individual defensive-work biases.
     */
    static float targetY(float baseX, float baseY, float centreY, int side, float ballX, float ballY,
                          TeamPhase phase,
                          TacticalState state, PlayerTacticalInstruction instruction) {
        float target = centreY + (baseY - centreY) / state.getCompactness();

        float possessionInfluence = possessionProfileInfluence(phase);
        if (possessionInfluence > 0) {
            // A slow profile is strongest in settled possession. Immediately after winning the
            // ball most of the legacy forward target is retained so counter-attacks remain viable.
            float attackingProgress = -side * (target - centreY);
            if (attackingProgress > 0) {
                float effectiveForwardRuns = 1
                    + (state.getForwardRunRate() - 1) * possessionInfluence;
                target = centreY - side * attackingProgress * effectiveForwardRuns;
            }
            float holdUpInfluence = phase == TeamPhase.ORGANIZED_POSSESSION
                ? 1 : TRANSITION_HOLD_UP_INFLUENCE;
            target += side * COME_SHORT_DISTANCE * instruction.getComeShort() * holdUpInfluence;
            target -= side * RUN_BEHIND_DISTANCE * instruction.getRunBehind() * holdUpInfluence;
        }

        float recoveryInfluence = defensiveRecoveryInfluence(phase);
        if (recoveryInfluence > 0) {
            if (sameFlank(baseX, ballX)) {
                float supportTarget = ballY + side * PRESS_SUPPORT_GOAL_SIDE_DISTANCE;
                float blend = PRESS_SUPPORT_Y_BLEND
                    * instruction.getPressSupport()
                    * instruction.getDefensiveWorkRate()
                    * defensivePressInfluence(phase);
                target += (supportTarget - target) * blend;
            }
            target += side * (
                DEFENSIVE_DEPTH_DISTANCE * instruction.getDefensiveDepth()
                    + TRACKING_BACK_DISTANCE * instruction.getTrackingBack()
            ) * recoveryInfluence;
        }

        return clamp(target, -Const.GOAL_LINE + 20, Const.GOAL_LINE - 20);
    }

    /** Returns whether a player target and the ball occupy the same meaningful flank. */
    private static boolean sameFlank(float playerX, float ballX) {
        return Math.abs(ballX) >= FLANK_THRESHOLD && Math.signum(playerX) == Math.signum(ballX);
    }

    /** Settled possession receives the full patience profile; a turnover receives only a light bias. */
    private static float possessionProfileInfluence(TeamPhase phase) {
        if (phase == TeamPhase.ORGANIZED_POSSESSION) return 1;
        if (phase == TeamPhase.ATTACKING_TRANSITION) return TRANSITION_POSSESSION_INFLUENCE;
        return 0;
    }

    /** Recovery depth is deliberately strongest immediately after possession is lost. */
    private static float defensiveRecoveryInfluence(TeamPhase phase) {
        if (phase == TeamPhase.DEFENDING_TRANSITION) {
            return DEFENSIVE_TRANSITION_RECOVERY_INFLUENCE;
        }
        return phase == TeamPhase.STABLE_DEFENSE ? 1 : 0;
    }

    /** Settled defending keeps support available but avoids permanent transition-level chasing. */
    private static float defensivePressInfluence(TeamPhase phase) {
        if (phase == TeamPhase.DEFENDING_TRANSITION) {
            return DEFENSIVE_TRANSITION_RECOVERY_INFLUENCE;
        }
        return phase == TeamPhase.STABLE_DEFENSE ? STABLE_DEFENSE_PRESS_INFLUENCE : 0;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
