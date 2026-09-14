package com.ygames.ysoccer.match;

import com.ygames.ysoccer.framework.EMath;
import com.ygames.ysoccer.framework.GLGame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Development-only observations of behavior produced by the current tactical state.
 *
 * <p>The collector is deliberately passive: it samples actual player positions and already
 * selected movement targets, and records passes only after {@link PlayerStateKick} has committed
 * to the passing action. No value from this class is read by AI decisions.</p>
 */
final class TacticalDebugMetrics {

    private static final float FORWARD_RUN_TARGET_DISTANCE = 30f;
    private static final float PASS_DIRECTION_COMPONENT = 0.35f;
    private static final int ABNORMALLY_SHORT_PHASE_FRAMES = 8;

    private long shapeSamples;
    private double horizontalSpreadTotal;
    private double lineGapTotal;
    private long lineGapSamples;
    private long forwardRunOpportunities;
    private long forwardRunTargets;
    private int forwardPasses;
    private int sidewaysPasses;
    private int backwardPasses;
    private int possessionPassSamples;
    private long possessionFramesAtPassTotal;
    private Player possessionOwner;
    private int possessionOwnerFrames;
    private long holdUpSamples;
    private double holdUpTargetDepthTotal;
    private long playerSevenDefendingSamples;
    private double playerSevenDefensiveDepthTotal;
    private double playerSevenBallDistanceTotal;
    private final long[] phaseFrames = new long[TeamPhase.values().length];
    private final int[][] phasePasses = new int[TeamPhase.values().length]
        [TacticalDecisionPolicy.PassDirection.values().length];
    private final List<PassObservation> passObservations = new ArrayList<>();
    private TeamPhase lastSampledPhase;
    private int lastSampledPhaseFrames;
    private boolean firstObservedPhase;
    private int phaseSwitches;
    private int abnormallyShortPhases;
    private Snapshot previous = Snapshot.empty();

    /** Clears current and previous observation windows for a new match. */
    void reset() {
        clearCurrent();
        previous = Snapshot.empty();
    }

    /**
     * Preserves the current window as the comparison baseline and starts a fresh measurement.
     * An empty current window never replaces an already useful baseline.
     */
    void startComparisonWindow() {
        if (!hasCurrentData()) return;
        previous = snapshot();
        clearCurrent();
    }

    private void clearCurrent() {
        shapeSamples = 0;
        horizontalSpreadTotal = 0;
        lineGapTotal = 0;
        lineGapSamples = 0;
        forwardRunOpportunities = 0;
        forwardRunTargets = 0;
        forwardPasses = 0;
        sidewaysPasses = 0;
        backwardPasses = 0;
        possessionPassSamples = 0;
        possessionFramesAtPassTotal = 0;
        possessionOwner = null;
        possessionOwnerFrames = 0;
        holdUpSamples = 0;
        holdUpTargetDepthTotal = 0;
        playerSevenDefendingSamples = 0;
        playerSevenDefensiveDepthTotal = 0;
        playerSevenBallDistanceTotal = 0;
        for (TeamPhase phase : TeamPhase.values()) {
            phaseFrames[phase.ordinal()] = 0;
            for (TacticalDecisionPolicy.PassDirection direction
                 : TacticalDecisionPolicy.PassDirection.values()) {
                phasePasses[phase.ordinal()][direction.ordinal()] = 0;
            }
        }
        passObservations.clear();
        lastSampledPhase = null;
        lastSampledPhaseFrames = 0;
        firstObservedPhase = true;
        phaseSwitches = 0;
        abnormallyShortPhases = 0;
    }

    /** Samples one 64 Hz AI frame from a live team without modifying match state. */
    void sample(Team team) {
        if (team == null) return;
        samplePhase(team.getTeamPhase());
        if (team.match == null || team.match.ball == null || team.lineup == null) {
            return;
        }

        Player owner = team.match.ball.owner;
        if (owner != null && owner.team == team) {
            if (owner == possessionOwner) {
                possessionOwnerFrames++;
            } else {
                possessionOwner = owner;
                possessionOwnerFrames = 1;
            }
        } else {
            possessionOwner = null;
            possessionOwnerFrames = 0;
        }

        int activePlayers = Math.min(Const.TEAM_SIZE, team.lineup.size());
        float centreX = 0;
        int outfieldCount = 0;
        for (int i = 1; i < activePlayers; i++) {
            Player player = team.lineup.get(i);
            if (!player.isActive) continue;
            centreX += player.x;
            outfieldCount++;
        }
        if (outfieldCount == 0) return;
        centreX /= outfieldCount;

        float spread = 0;
        float defenderDepth = 0;
        float midfielderDepth = 0;
        float attackerDepth = 0;
        int defenders = 0;
        int midfielders = 0;
        int attackers = 0;
        for (int i = 1; i < activePlayers; i++) {
            Player player = team.lineup.get(i);
            if (!player.isActive) continue;
            spread += Math.abs(player.x - centreX);
            float attackingDepth = -team.side * player.y;
            if (isDefender(player)) {
                defenderDepth += attackingDepth;
                defenders++;
            } else if (player.role == Player.Role.ATTACKER) {
                attackerDepth += attackingDepth;
                attackers++;
            } else if (player.role != Player.Role.GOALKEEPER) {
                midfielderDepth += attackingDepth;
                midfielders++;
            }
        }
        horizontalSpreadTotal += spread / outfieldCount;
        shapeSamples++;
        if (defenders > 0 && midfielders > 0 && attackers > 0) {
            defenderDepth /= defenders;
            midfielderDepth /= midfielders;
            attackerDepth /= attackers;
            lineGapTotal += Math.abs(defenderDepth - midfielderDepth)
                + Math.abs(midfielderDepth - attackerDepth);
            lineGapSamples++;
        }

        if (owner != null && owner.team == team) {
            sampleForwardRuns(team, owner, activePlayers);
            sampleHoldUpForward(team);
        } else if (owner != null) {
            samplePlayerSevenDefending(team);
        }
    }

    /**
     * Records a pass only after the normal kick state commits it, including the phase, route
     * geometry, lane estimate, and coach values that were visible to AI at that simulation frame.
     */
    void recordPass(Team team, Player passer, Player receiver, float ballAngle) {
        if (team == null || passer == null || passer.team != team) return;
        float attackingComponent = -team.side * EMath.sin(ballAngle);
        TacticalDecisionPolicy.PassDirection direction;
        if (attackingComponent > PASS_DIRECTION_COMPONENT) {
            forwardPasses++;
            direction = TacticalDecisionPolicy.PassDirection.FORWARD;
        } else if (attackingComponent < -PASS_DIRECTION_COMPONENT) {
            backwardPasses++;
            direction = TacticalDecisionPolicy.PassDirection.BACKWARD;
        } else {
            sidewaysPasses++;
            direction = TacticalDecisionPolicy.PassDirection.SIDEWAYS;
        }
        TeamPhase phase = team.getTeamPhase();
        phasePasses[phase.ordinal()][direction.ordinal()]++;
        if (possessionOwner == passer) {
            possessionFramesAtPassTotal += possessionOwnerFrames;
            possessionPassSamples++;
        }

        TacticalState state = team.getTacticalState();
        PlayerTacticalInstruction instruction = state.getInstruction(passer);
        float distance = receiver == null ? Float.NaN : receiver.distanceFrom(passer);
        float laneSafety = receiver == null ? Float.NaN : passer.passingLaneSafety(receiver);
        passObservations.add(new PassObservation(
            phase,
            team.getTeamPhaseFrames(),
            distance,
            direction,
            laneSafety,
            state.getWidth(),
            state.getCompactness(),
            state.getTempo(),
            state.getPassingRisk(),
            state.getForwardRunRate(),
            state.getBallRetention(),
            state.getBackPassPreference(),
            instruction.getDefensiveWorkRate(),
            instruction.getHoldUpPlay(),
            instruction.getLayoffPreference()
        ));
    }

    private void samplePhase(TeamPhase phase) {
        phaseFrames[phase.ordinal()]++;
        if (lastSampledPhase == null) {
            lastSampledPhase = phase;
            lastSampledPhaseFrames = 1;
            return;
        }
        if (lastSampledPhase == phase) {
            lastSampledPhaseFrames++;
            return;
        }

        phaseSwitches++;
        // The first partial phase after a match/window reset has no observable start boundary,
        // so it cannot be called anomalously short. Subsequent completed phases can be measured.
        if (!firstObservedPhase && lastSampledPhaseFrames < ABNORMALLY_SHORT_PHASE_FRAMES) {
            abnormallyShortPhases++;
        }
        firstObservedPhase = false;
        lastSampledPhase = phase;
        lastSampledPhaseFrames = 1;
    }

    private void sampleForwardRuns(Team team, Player owner, int activePlayers) {
        for (int i = 1; i < activePlayers; i++) {
            Player player = team.lineup.get(i);
            if (!player.isActive || player == owner) continue;
            forwardRunOpportunities++;
            float targetProgress = -team.side * (player.ty - player.y);
            if (targetProgress > FORWARD_RUN_TARGET_DISTANCE) forwardRunTargets++;
        }
    }

    private void sampleHoldUpForward(Team team) {
        Player forward = team.findPrimaryAttacker();
        if (forward == null) return;
        PlayerTacticalInstruction instruction = team.getTacticalState().getInstruction(forward);
        if (instruction.getHoldUpPlay() <= 0) return;
        holdUpTargetDepthTotal += -team.side * forward.ty;
        holdUpSamples++;
    }

    private void samplePlayerSevenDefending(Team team) {
        Player playerSeven = team.findPlayerByNumber(7);
        if (playerSeven == null) return;
        PlayerTacticalInstruction instruction = team.getTacticalState().getInstruction(playerSeven);
        if (instruction.getDefensiveWorkRate() <= 0) return;
        playerSevenDefensiveDepthTotal += team.side * playerSeven.y;
        playerSevenBallDistanceTotal += playerSeven.ballDistance;
        playerSevenDefendingSamples++;
    }

    private boolean isDefender(Player player) {
        return player.role == Player.Role.DEFENDER
            || player.role == Player.Role.LEFT_BACK
            || player.role == Player.Role.RIGHT_BACK;
    }

    float getObservationSeconds() {
        return shapeSamples / (float) GLGame.VIRTUAL_REFRESH_RATE;
    }

    float getAverageHorizontalSpread() {
        return shapeSamples == 0 ? 0 : (float) (horizontalSpreadTotal / shapeSamples);
    }

    float getAverageLineGap() {
        return lineGapSamples == 0 ? 0 : (float) (lineGapTotal / lineGapSamples);
    }

    float getForwardRunRatio() {
        return forwardRunOpportunities == 0 ? 0 : forwardRunTargets / (float) forwardRunOpportunities;
    }

    int getForwardPasses() {
        return forwardPasses;
    }

    int getSidewaysPasses() {
        return sidewaysPasses;
    }

    int getBackwardPasses() {
        return backwardPasses;
    }

    /** Returns observed simulation time in one phase for the current comparison window. */
    float getPhaseSeconds(TeamPhase phase) {
        return phaseFrames[phase.ordinal()] / (float) GLGame.VIRTUAL_REFRESH_RATE;
    }

    /** Returns the number of observed phase boundaries in the current comparison window. */
    int getPhaseSwitches() {
        return phaseSwitches;
    }

    /** Returns completed phases shorter than the telemetry anomaly threshold. */
    int getAbnormallyShortPhases() {
        return abnormallyShortPhases;
    }

    /** Returns committed passes of one direction while the supplied phase was active. */
    int getPhasePasses(TeamPhase phase, TacticalDecisionPolicy.PassDirection direction) {
        return phasePasses[phase.ordinal()][direction.ordinal()];
    }

    /** Returns a direction's share of all passes committed in one phase. */
    float getPhasePassRatio(TeamPhase phase, TacticalDecisionPolicy.PassDirection direction) {
        int[] passes = phasePasses[phase.ordinal()];
        int total = 0;
        for (int count : passes) total += count;
        return total == 0 ? 0 : passes[direction.ordinal()] / (float) total;
    }

    /** Returns immutable per-pass observations retained for this development window. */
    List<PassObservation> getPassObservations() {
        return Collections.unmodifiableList(passObservations);
    }

    float getAveragePossessionSecondsAtPass() {
        return possessionPassSamples == 0
            ? 0
            : possessionFramesAtPassTotal / (float) possessionPassSamples
                / GLGame.VIRTUAL_REFRESH_RATE;
    }

    float getAverageHoldUpTargetDepth() {
        return holdUpSamples == 0 ? 0 : (float) (holdUpTargetDepthTotal / holdUpSamples);
    }

    float getAveragePlayerSevenDefensiveDepth() {
        return playerSevenDefendingSamples == 0
            ? 0
            : (float) (playerSevenDefensiveDepthTotal / playerSevenDefendingSamples);
    }

    float getAveragePlayerSevenBallDistance() {
        return playerSevenDefendingSamples == 0
            ? 0
            : (float) (playerSevenBallDistanceTotal / playerSevenDefendingSamples);
    }

    boolean hasPreviousWindow() {
        return previous.present;
    }

    float getPreviousHorizontalSpread() {
        return previous.horizontalSpread;
    }

    float getPreviousLineGap() {
        return previous.lineGap;
    }

    float getPreviousForwardRunRatio() {
        return previous.forwardRunRatio;
    }

    int getPreviousForwardPasses() {
        return previous.forwardPasses;
    }

    int getPreviousSidewaysPasses() {
        return previous.sidewaysPasses;
    }

    int getPreviousBackwardPasses() {
        return previous.backwardPasses;
    }

    float getPreviousPossessionSecondsAtPass() {
        return previous.possessionSecondsAtPass;
    }

    float getPreviousPhaseSeconds(TeamPhase phase) {
        return previous.phaseFrames[phase.ordinal()] / (float) GLGame.VIRTUAL_REFRESH_RATE;
    }

    int getPreviousPhaseSwitches() {
        return previous.phaseSwitches;
    }

    int getPreviousAbnormallyShortPhases() {
        return previous.abnormallyShortPhases;
    }

    float getPreviousPhasePassRatio(TeamPhase phase,
                                    TacticalDecisionPolicy.PassDirection direction) {
        int[] passes = previous.phasePasses[phase.ordinal()];
        int total = 0;
        for (int count : passes) total += count;
        return total == 0 ? 0 : passes[direction.ordinal()] / (float) total;
    }

    private boolean hasCurrentData() {
        if (shapeSamples > 0 || forwardPasses + sidewaysPasses + backwardPasses > 0) return true;
        for (long frames : phaseFrames) {
            if (frames > 0) return true;
        }
        return false;
    }

    private Snapshot snapshot() {
        return new Snapshot(
            true,
            getAverageHorizontalSpread(),
            getAverageLineGap(),
            getForwardRunRatio(),
            forwardPasses,
            sidewaysPasses,
            backwardPasses,
            getAveragePossessionSecondsAtPass(),
            phaseFrames,
            phasePasses,
            phaseSwitches,
            abnormallyShortPhases
        );
    }

    /**
     * Immutable record of a committed pass and the tactical facts available at that AI moment.
     * NaN distance or lane safety means a manual/non-corrected pass had no explicit receiver.
     */
    static final class PassObservation {
        private final TeamPhase phase;
        private final int phaseFrame;
        private final float distance;
        private final TacticalDecisionPolicy.PassDirection direction;
        private final float laneSafety;
        private final float width;
        private final float compactness;
        private final float tempo;
        private final float passingRisk;
        private final float forwardRunRate;
        private final float ballRetention;
        private final float backPassPreference;
        private final float defensiveWorkRate;
        private final float holdUpPlay;
        private final float layoffPreference;

        PassObservation(TeamPhase phase, int phaseFrame, float distance,
                        TacticalDecisionPolicy.PassDirection direction, float laneSafety,
                        float width, float compactness, float tempo, float passingRisk,
                        float forwardRunRate, float ballRetention, float backPassPreference,
                        float defensiveWorkRate, float holdUpPlay, float layoffPreference) {
            this.phase = phase;
            this.phaseFrame = phaseFrame;
            this.distance = distance;
            this.direction = direction;
            this.laneSafety = laneSafety;
            this.width = width;
            this.compactness = compactness;
            this.tempo = tempo;
            this.passingRisk = passingRisk;
            this.forwardRunRate = forwardRunRate;
            this.ballRetention = ballRetention;
            this.backPassPreference = backPassPreference;
            this.defensiveWorkRate = defensiveWorkRate;
            this.holdUpPlay = holdUpPlay;
            this.layoffPreference = layoffPreference;
        }

        TeamPhase getPhase() {
            return phase;
        }

        int getPhaseFrame() {
            return phaseFrame;
        }

        float getDistance() {
            return distance;
        }

        TacticalDecisionPolicy.PassDirection getDirection() {
            return direction;
        }

        float getLaneSafety() {
            return laneSafety;
        }

        float getWidth() {
            return width;
        }

        float getCompactness() {
            return compactness;
        }

        float getTempo() {
            return tempo;
        }

        float getPassingRisk() {
            return passingRisk;
        }

        float getForwardRunRate() {
            return forwardRunRate;
        }

        float getBallRetention() {
            return ballRetention;
        }

        float getBackPassPreference() {
            return backPassPreference;
        }

        float getDefensiveWorkRate() {
            return defensiveWorkRate;
        }

        float getHoldUpPlay() {
            return holdUpPlay;
        }

        float getLayoffPreference() {
            return layoffPreference;
        }
    }

    /** Immutable values from the observation window that preceded the latest debug command. */
    private static final class Snapshot {
        final boolean present;
        final float horizontalSpread;
        final float lineGap;
        final float forwardRunRatio;
        final int forwardPasses;
        final int sidewaysPasses;
        final int backwardPasses;
        final float possessionSecondsAtPass;
        final long[] phaseFrames;
        final int[][] phasePasses;
        final int phaseSwitches;
        final int abnormallyShortPhases;

        Snapshot(boolean present, float horizontalSpread, float lineGap, float forwardRunRatio,
                  int forwardPasses, int sidewaysPasses, int backwardPasses,
                  float possessionSecondsAtPass, long[] phaseFrames, int[][] phasePasses,
                  int phaseSwitches, int abnormallyShortPhases) {
            this.present = present;
            this.horizontalSpread = horizontalSpread;
            this.lineGap = lineGap;
            this.forwardRunRatio = forwardRunRatio;
            this.forwardPasses = forwardPasses;
            this.sidewaysPasses = sidewaysPasses;
            this.backwardPasses = backwardPasses;
            this.possessionSecondsAtPass = possessionSecondsAtPass;
            this.phaseFrames = phaseFrames.clone();
            this.phasePasses = new int[phasePasses.length][];
            for (int i = 0; i < phasePasses.length; i++) {
                this.phasePasses[i] = phasePasses[i].clone();
            }
            this.phaseSwitches = phaseSwitches;
            this.abnormallyShortPhases = abnormallyShortPhases;
        }

        static Snapshot empty() {
            return new Snapshot(
                false, 0, 0, 0, 0, 0, 0, 0,
                new long[TeamPhase.values().length],
                new int[TeamPhase.values().length]
                    [TacticalDecisionPolicy.PassDirection.values().length],
                0, 0
            );
        }
    }
}
