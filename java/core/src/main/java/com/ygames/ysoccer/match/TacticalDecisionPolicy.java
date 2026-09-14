package com.ygames.ysoccer.match;

/**
 * Small, deterministic scoring policy that translates coach intent into AI preferences.
 *
 * <p>The policy supplies biases only. Existing state eligibility, ball physics, player skills,
 * and random draws still decide whether an action can start and how it is executed. Keeping the
 * calculations pure also allows behavior trends to be tested without a graphics context.</p>
 */
final class TacticalDecisionPolicy {

    enum PassDirection {FORWARD, SIDEWAYS, BACKWARD}

    private static final float PASS_DIRECTION_THRESHOLD = 30f;
    private static final float TRANSITION_POSSESSION_INFLUENCE = 0.20f;
    private static final float TRANSITION_PLAYER_INFLUENCE = 0.65f;
    private static final float DEFENSIVE_TRANSITION_INFLUENCE = 1.20f;
    private static final float STABLE_DEFENSE_INFLUENCE = 0.75f;

    private TacticalDecisionPolicy() {
    }

    /** Classifies receiver progress in the attacking coordinate system. */
    static PassDirection classifyPass(float attackingProgress) {
        if (attackingProgress > PASS_DIRECTION_THRESHOLD) return PassDirection.FORWARD;
        if (attackingProgress < -PASS_DIRECTION_THRESHOLD) return PassDirection.BACKWARD;
        return PassDirection.SIDEWAYS;
    }

    /** Returns whether this player needs direction-aware passing instead of the legacy narrow cone. */
    static boolean usesTacticalPassing(TacticalState state, PlayerTacticalInstruction instruction,
                                       TeamPhase phase) {
        return phase.hasTeamPossession()
            && (state.usesTacticalPassing() || instruction.getLayoffPreference() > 0);
    }

    /**
     * Scores a legal pass option. Higher values favor safe, reachable options while coach intent
     * changes the relative value of forward, sideways, and backward receivers.
     */
    static float passOptionScore(float attackingProgress, float distance, float angleDifference,
                                  float laneSafety, TacticalState state,
                                  PlayerTacticalInstruction instruction, TeamPhase phase) {
        float phaseInfluence = possessionProfileInfluence(phase);
        float passingRisk = phasedValue(state.getPassingRisk(), 1, phaseInfluence);
        float ballRetention = phasedValue(state.getBallRetention(), 1, phaseInfluence);
        float backPassPreference = phasedValue(
            state.getBackPassPreference(), 1, phaseInfluence);
        float playerInfluence = attackingPlayerInfluence(phase);
        float distanceQuality = 1 - clamp(distance / 450f, 0, 1);
        float alignment = 1 - clamp(Math.abs(angleDifference) / 180f, 0, 1);
        float normalizedProgress = clamp(Math.abs(attackingProgress) / 300f, 0, 1);
        float safetyWeight = 1.3f + Math.max(0, 1 - passingRisk);

        float score = distanceQuality * 0.4f + alignment * 0.5f + laneSafety * safetyWeight;
        switch (classifyPass(attackingProgress)) {
            case FORWARD:
                score += passingRisk * normalizedProgress;
                score -= Math.max(0, 1 - passingRisk) * 1.5f;
                break;

            case SIDEWAYS:
                score += Math.max(0, ballRetention - 1) * 0.8f;
                score += Math.max(0, 1 - passingRisk) * 0.8f;
                score += instruction.getLayoffPreference() * playerInfluence * 0.5f;
                break;

            case BACKWARD:
                score += Math.max(0, backPassPreference - 1) * 1.2f;
                score += Math.max(0, ballRetention - 1) * 0.7f;
                score += Math.max(0, 1 - passingRisk) * 0.7f;
                score += instruction.getLayoffPreference() * playerInfluence;
                break;
        }
        return score;
    }

    /** Returns the probability of passing at an eligible decision point. */
    static float passingProbability(float legacyProbability, TacticalState state,
                                     PlayerTacticalInstruction instruction, TeamPhase phase) {
        float phaseInfluence = possessionProfileInfluence(phase);
        float tempo = phasedValue(state.getTempo(), 1, phaseInfluence);
        float ballRetention = phasedValue(state.getBallRetention(), 1, phaseInfluence);
        float playerInfluence = attackingPlayerInfluence(phase);
        float holdUpMultiplier = 1
            + 0.75f * instruction.getLayoffPreference() * playerInfluence
            + 0.25f * instruction.getHoldUpPlay() * playerInfluence;
        float probability = legacyProbability
            * tempo
            / ballRetention
            * holdUpMultiplier;
        return clamp(probability, 0.05f, 0.85f);
    }

    /** Reduces rushed direct-shot decisions when the team or player is instructed to be patient. */
    static float shootingProbability(float legacyProbability, TacticalState state,
                                     PlayerTacticalInstruction instruction, TeamPhase phase) {
        float phaseInfluence = possessionProfileInfluence(phase);
        float patience = phasedValue(state.getTempo(), 1, phaseInfluence)
            / phasedValue(state.getBallRetention(), 1, phaseInfluence);
        float holdUpMultiplier = 1
            - 0.45f * instruction.getHoldUpPlay() * attackingPlayerInfluence(phase);
        return legacyProbability * clamp(patience * holdUpMultiplier, 0.4f, 1.25f);
    }

    /** Increases the existing teammate steering influence for retention and hold-up play. */
    static float mateInfluence(TacticalState state, PlayerTacticalInstruction instruction,
                               TeamPhase phase) {
        float phaseInfluence = possessionProfileInfluence(phase);
        return phasedValue(state.getBallRetention(), 1, phaseInfluence)
            * (1 + 0.6f * instruction.getHoldUpPlay() * attackingPlayerInfluence(phase));
    }

    /** Reduces direct goal steering when risk is lowered or a forward is instructed to hold play up. */
    static float goalInfluence(TacticalState state, PlayerTacticalInstruction instruction,
                               TeamPhase phase) {
        float phaseInfluence = possessionProfileInfluence(phase);
        return Math.max(0.35f,
            phasedValue(state.getPassingRisk(), 1, phaseInfluence)
                * (1 - 0.45f * instruction.getHoldUpPlay()
                    * attackingPlayerInfluence(phase)));
    }

    /** Makes a high-work-rate player modestly more eligible for primary pressure without forcing selection. */
    static float effectiveDefenderDistance(float physicalDistance,
                                            PlayerTacticalInstruction instruction,
                                            TeamPhase phase) {
        float phaseInfluence = defensiveInstructionInfluence(phase);
        float involvement = 1
            + 0.18f * instruction.getDefensiveWorkRate() * phaseInfluence
            + 0.12f * instruction.getPressSupport() * phaseInfluence;
        return physicalDistance / involvement;
    }

    /** Returns a shorter direction-update interval for a more active individual defender. */
    static int defendingUpdateInterval(int baseInterval, PlayerTacticalInstruction instruction,
                                       TeamPhase phase) {
        float phaseInfluence = defensiveInstructionInfluence(phase);
        float activity = 1
            + 0.5f * instruction.getDefensiveWorkRate() * phaseInfluence
            + 0.2f * instruction.getPressSupport() * phaseInfluence;
        return Math.max(1, Math.round(baseInterval / activity));
    }

    /** Returns how strongly the settled possession profile applies in the supplied phase. */
    private static float possessionProfileInfluence(TeamPhase phase) {
        if (phase == TeamPhase.ORGANIZED_POSSESSION) return 1;
        if (phase == TeamPhase.ATTACKING_TRANSITION) return TRANSITION_POSSESSION_INFLUENCE;
        return 0;
    }

    /** A hold-up instruction remains active on turnover, but does not erase the chance to break. */
    private static float attackingPlayerInfluence(TeamPhase phase) {
        if (phase == TeamPhase.ORGANIZED_POSSESSION) return 1;
        if (phase == TeamPhase.ATTACKING_TRANSITION) return TRANSITION_PLAYER_INFLUENCE;
        return 0;
    }

    /** Individual recovery and pressure are strongest just after this team loses possession. */
    private static float defensiveInstructionInfluence(TeamPhase phase) {
        if (phase == TeamPhase.DEFENDING_TRANSITION) return DEFENSIVE_TRANSITION_INFLUENCE;
        if (phase == TeamPhase.STABLE_DEFENSE) return STABLE_DEFENSE_INFLUENCE;
        return 0;
    }

    private static float phasedValue(float value, float neutral, float influence) {
        return neutral + (value - neutral) * influence;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
