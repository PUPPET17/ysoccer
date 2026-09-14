package com.ygames.ysoccer.match;

/**
 * Stable match context from one team's point of view.
 *
 * <p>The phase describes whether coach preferences should be interpreted as possession,
 * transition, or defensive intent. It does not choose an AI state or action. In particular,
 * transition phases remain ordinary {@link AiFsm} and {@link PlayerFsm} play; they only let the
 * existing tactical biases distinguish an immediate turnover response from settled play.</p>
 */
public enum TeamPhase {
    /** No side has supplied enough recent ownership evidence to claim reliable control. */
    DISPUTED,
    /** This team has just established control and may exploit the turnover before reorganizing. */
    ATTACKING_TRANSITION,
    /** This team has retained the ball long enough to apply its organized possession profile. */
    ORGANIZED_POSSESSION,
    /** The opponent has just established control and this team is recovering after losing it. */
    DEFENDING_TRANSITION,
    /** The opponent has retained the ball long enough for this team to defend in settled shape. */
    STABLE_DEFENSE;

    /** Returns whether the phase represents confirmed possession by this team. */
    boolean hasTeamPossession() {
        return this == ATTACKING_TRANSITION || this == ORGANIZED_POSSESSION;
    }

    /** Returns whether the phase represents confirmed possession by the opponent. */
    boolean hasOpponentPossession() {
        return this == DEFENDING_TRANSITION || this == STABLE_DEFENSE;
    }
}
