package com.ygames.ysoccer.match;

/**
 * Derives a debounced {@link TeamPhase} from simulation-frame ball ownership.
 *
 * <p>The tracker is owned by one {@link Team} and advances exactly once per 64 Hz AI frame. It
 * uses neither wall-clock time nor random values. A new owner must remain observable for several
 * frames before control changes, and a short ownerless interval retains the last confirmed side.
 * This prevents tackles and ordinary passes from flipping phase every frame while keeping the
 * result reproducible for an identical ownership sequence.</p>
 */
final class TeamPhaseTracker {

    /** Consecutive owner frames required before a turnover is accepted as real control. */
    static final int OWNERSHIP_CONFIRMATION_FRAMES = 3;
    /** Ownerless frames tolerated as a pass/tackle gap before control becomes disputed. */
    static final int LOOSE_BALL_GRACE_FRAMES = 32;
    /** Confirmed ownership frames required to move from turnover response to settled play. */
    static final int SETTLED_POSSESSION_FRAMES = 48;

    enum Ownership {TEAM, OPPONENT, NONE}

    private TeamPhase phase;
    private Ownership confirmedOwnership;
    private Ownership candidateOwnership;
    private int candidateFrames;
    private int looseBallFrames;
    private int possessionFrames;
    private int phaseFrames;

    TeamPhaseTracker() {
        reset();
    }

    /** Clears all ownership evidence at a match or training lifecycle boundary. */
    void reset() {
        phase = TeamPhase.DISPUTED;
        confirmedOwnership = Ownership.NONE;
        candidateOwnership = Ownership.NONE;
        candidateFrames = 0;
        looseBallFrames = 0;
        possessionFrames = 0;
        phaseFrames = 0;
    }

    /**
     * Samples current ball ownership for this team and advances the deterministic phase state.
     * The ball's historical {@code ownerLast} is intentionally not treated as current evidence:
     * it has no age and can remain populated throughout a long loose-ball period.
     */
    void update(Team team, Ball ball) {
        Ownership observed = Ownership.NONE;
        if (team != null && ball != null && ball.owner != null) {
            observed = ball.owner.team == team ? Ownership.TEAM : Ownership.OPPONENT;
        }
        update(observed);
    }

    /** Advances the tracker from an explicit ownership fact used by deterministic scenarios. */
    void update(Ownership observed) {
        if (observed == null) observed = Ownership.NONE;

        if (observed == Ownership.NONE) {
            candidateOwnership = Ownership.NONE;
            candidateFrames = 0;
            looseBallFrames++;
            if (confirmedOwnership != Ownership.NONE
                && looseBallFrames > LOOSE_BALL_GRACE_FRAMES) {
                confirmedOwnership = Ownership.NONE;
                possessionFrames = 0;
            }
        } else if (observed == confirmedOwnership) {
            looseBallFrames = 0;
            candidateOwnership = Ownership.NONE;
            candidateFrames = 0;
            possessionFrames++;
        } else {
            looseBallFrames = 0;
            if (candidateOwnership == observed) {
                candidateFrames++;
            } else {
                candidateOwnership = observed;
                candidateFrames = 1;
            }

            // Evidence is accumulated before replacing the confirmed side, providing turnover
            // hysteresis without delaying or consuming any existing AI random decision.
            if (candidateFrames >= OWNERSHIP_CONFIRMATION_FRAMES) {
                confirmedOwnership = observed;
                possessionFrames = candidateFrames;
                candidateOwnership = Ownership.NONE;
                candidateFrames = 0;
            }
        }

        updatePhase(derivePhase());
    }

    private TeamPhase derivePhase() {
        switch (confirmedOwnership) {
            case TEAM:
                return possessionFrames >= SETTLED_POSSESSION_FRAMES
                    ? TeamPhase.ORGANIZED_POSSESSION
                    : TeamPhase.ATTACKING_TRANSITION;
            case OPPONENT:
                return possessionFrames >= SETTLED_POSSESSION_FRAMES
                    ? TeamPhase.STABLE_DEFENSE
                    : TeamPhase.DEFENDING_TRANSITION;
            case NONE:
            default:
                return TeamPhase.DISPUTED;
        }
    }

    private void updatePhase(TeamPhase next) {
        if (next == phase) {
            phaseFrames++;
        } else {
            phase = next;
            phaseFrames = 1;
        }
    }

    TeamPhase getPhase() {
        return phase;
    }

    int getPhaseFrames() {
        return phaseFrames;
    }

    int getPossessionFrames() {
        return possessionFrames;
    }

    Ownership getConfirmedOwnership() {
        return confirmedOwnership;
    }
}
