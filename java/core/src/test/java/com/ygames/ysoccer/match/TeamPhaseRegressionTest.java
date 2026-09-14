package com.ygames.ysoccer.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Deterministic simulation-frame scenarios for team phases and phase-aware tactical behavior.
 *
 * <p>This runner deliberately stops short of pretending that the automatic score simulator is a
 * live match. It feeds production phase, positioning, decision, and telemetry classes explicit
 * ownership frames, allowing transition boundaries and tactical trends to be repeated without a
 * graphics context or a wall clock.</p>
 */
public final class TeamPhaseRegressionTest {

    private static final float EPSILON = 0.001f;
    private static int checks;

    /** Runs all phase scenarios and exits nonzero when a transition or tactical trend regresses. */
    public static void main(String[] args) {
        fixedOwnershipSequenceCrossesAllBoundaries();
        briefContestsDoNotCausePhaseJitter();
        teamAiLoopSamplesLiveBallOwnership();
        lifecycleBoundariesClearOwnershipEvidence();
        phaseContextChangesProductionTacticalTrends();
        phaseContextChangesProductionReceiverChoice();
        neutralTacticsPreserveLegacyDecisionPath();
        telemetryCapturesPhaseAndPassSnapshots();
        fixedSeedScenarioIsRepeatable();
        System.out.println("Team phase regression checks passed: " + checks);
    }

    /** The Player receiver search must convert phase-aware scores into a different real choice. */
    private static void phaseContextChangesProductionReceiverChoice() {
        Match match = new Match();
        match.ball = new Ball(new SceneSettings());
        Team team = new Team();
        team.match = match;
        team.index = Match.HOME;
        team.side = 1;
        team.lineup = new ArrayList<>();
        match.team[Match.HOME] = team;

        Player carrier = preparedPlayer(team, match, 0, 0, 1);
        carrier.a = -90;
        Player forward = preparedPlayer(team, match, 0, -150, 1);
        preparedPlayer(team, match, 150, 0, 2);
        Player backward = preparedPlayer(team, match, 0, 150, 3);
        for (int i = 4; i < Const.TEAM_SIZE; i++) {
            preparedPlayer(team, match, 600 + 20 * i, 600, Const.BALL_PREDICTION);
        }
        match.ball.owner = carrier;
        team.getTacticalState().setPossessionProfile(0.65f, 0.55f, 0.55f, 1.45f, 1.60f);
        converge(team.getTacticalState());

        repeat(team.getTeamPhaseTracker(), TeamPhaseTracker.Ownership.TEAM,
            TeamPhaseTracker.OWNERSHIP_CONFIRMATION_FRAMES);
        carrier.searchTacticalPassingMate();
        check(carrier.passingMate == forward,
            "attacking transition retains the progressive receiver in production Player search");

        repeat(team.getTeamPhaseTracker(), TeamPhaseTracker.Ownership.TEAM,
            TeamPhaseTracker.SETTLED_POSSESSION_FRAMES
                - TeamPhaseTracker.OWNERSHIP_CONFIRMATION_FRAMES);
        carrier.searchTacticalPassingMate();
        check(carrier.passingMate == backward,
            "organized slow possession selects the conservative outlet in production Player search");
    }

    /** The production 64 Hz team AI entry point must sample the live ball exactly once per call. */
    private static void teamAiLoopSamplesLiveBallOwnership() {
        Match match = new Match();
        match.ball = new Ball(new SceneSettings());
        Team team = new Team();
        team.match = match;
        team.lineup = new ArrayList<>();
        match.team[Match.HOME] = team;
        Player owner = new Player();
        owner.team = team;
        match.ball.owner = owner;

        for (int i = 0; i < TeamPhaseTracker.OWNERSHIP_CONFIRMATION_FRAMES; i++) {
            team.updateLineupAi();
        }
        check(team.getTeamPhase() == TeamPhase.ATTACKING_TRANSITION,
            "Team.updateLineupAi samples current match ownership");
        for (int i = TeamPhaseTracker.OWNERSHIP_CONFIRMATION_FRAMES;
             i < TeamPhaseTracker.SETTLED_POSSESSION_FRAMES; i++) {
            team.updateLineupAi();
        }
        check(team.getTeamPhase() == TeamPhase.ORGANIZED_POSSESSION,
            "production team AI loop reaches organized possession on the frame boundary");
        check(team.getTeamPhaseTracker().getPossessionFrames()
                == TeamPhaseTracker.SETTLED_POSSESSION_FRAMES,
            "production team AI loop advances one ownership frame per call");
    }

    /** Confirmed gains and losses must pass through transition before becoming settled. */
    private static void fixedOwnershipSequenceCrossesAllBoundaries() {
        TeamPhaseTracker tracker = new TeamPhaseTracker();
        check(tracker.getPhase() == TeamPhase.DISPUTED, "new tracker begins disputed");

        repeat(tracker, TeamPhaseTracker.Ownership.TEAM,
            TeamPhaseTracker.OWNERSHIP_CONFIRMATION_FRAMES - 1);
        check(tracker.getPhase() == TeamPhase.DISPUTED,
            "unconfirmed team possession remains disputed");
        tracker.update(TeamPhaseTracker.Ownership.TEAM);
        check(tracker.getPhase() == TeamPhase.ATTACKING_TRANSITION,
            "confirmed gain enters attacking transition");

        repeat(tracker, TeamPhaseTracker.Ownership.TEAM,
            TeamPhaseTracker.SETTLED_POSSESSION_FRAMES
                - TeamPhaseTracker.OWNERSHIP_CONFIRMATION_FRAMES - 1);
        check(tracker.getPhase() == TeamPhase.ATTACKING_TRANSITION,
            "possession remains transitional one frame before the settled boundary");
        tracker.update(TeamPhaseTracker.Ownership.TEAM);
        check(tracker.getPhase() == TeamPhase.ORGANIZED_POSSESSION,
            "sustained control becomes organized possession");

        repeat(tracker, TeamPhaseTracker.Ownership.OPPONENT,
            TeamPhaseTracker.OWNERSHIP_CONFIRMATION_FRAMES - 1);
        check(tracker.getPhase() == TeamPhase.ORGANIZED_POSSESSION,
            "opponent ownership is debounced before a confirmed loss");
        tracker.update(TeamPhaseTracker.Ownership.OPPONENT);
        check(tracker.getPhase() == TeamPhase.DEFENDING_TRANSITION,
            "confirmed loss enters defensive transition");

        repeat(tracker, TeamPhaseTracker.Ownership.OPPONENT,
            TeamPhaseTracker.SETTLED_POSSESSION_FRAMES
                - TeamPhaseTracker.OWNERSHIP_CONFIRMATION_FRAMES - 1);
        check(tracker.getPhase() == TeamPhase.DEFENDING_TRANSITION,
            "defending remains transitional one frame before the settled boundary");
        tracker.update(TeamPhaseTracker.Ownership.OPPONENT);
        check(tracker.getPhase() == TeamPhase.STABLE_DEFENSE,
            "sustained opponent control becomes stable defense");
    }

    /** Short wrong-owner or loose-ball observations must not produce a phase switch. */
    private static void briefContestsDoNotCausePhaseJitter() {
        TeamPhaseTracker tracker = settledTracker(TeamPhaseTracker.Ownership.TEAM);

        repeat(tracker, TeamPhaseTracker.Ownership.OPPONENT,
            TeamPhaseTracker.OWNERSHIP_CONFIRMATION_FRAMES - 1);
        tracker.update(TeamPhaseTracker.Ownership.TEAM);
        check(tracker.getPhase() == TeamPhase.ORGANIZED_POSSESSION,
            "brief opponent touch does not flip organized possession");
        check(tracker.getConfirmedOwnership() == TeamPhaseTracker.Ownership.TEAM,
            "brief opponent touch does not replace confirmed owner");

        repeat(tracker, TeamPhaseTracker.Ownership.NONE,
            TeamPhaseTracker.LOOSE_BALL_GRACE_FRAMES);
        check(tracker.getPhase() == TeamPhase.ORGANIZED_POSSESSION,
            "pass-length loose interval remains with recent confirmed control");
        tracker.update(TeamPhaseTracker.Ownership.NONE);
        check(tracker.getPhase() == TeamPhase.DISPUTED,
            "long ownerless interval eventually becomes disputed");
    }

    /** New match and training setup must not inherit confirmed control or a settled phase. */
    private static void lifecycleBoundariesClearOwnershipEvidence() {
        Team team = new Team();
        repeat(team.getTeamPhaseTracker(), TeamPhaseTracker.Ownership.OPPONENT,
            TeamPhaseTracker.SETTLED_POSSESSION_FRAMES);

        Match match = new Match();
        match.setSettings(new MatchSettings());
        team.beforeMatch(match);
        check(team.getTeamPhase() == TeamPhase.DISPUTED,
            "beforeMatch clears phase inherited from an earlier scene");
        check(team.getTeamPhaseTracker().getPossessionFrames() == 0,
            "beforeMatch clears ownership duration evidence");

        repeat(team.getTeamPhaseTracker(), TeamPhaseTracker.Ownership.TEAM,
            TeamPhaseTracker.SETTLED_POSSESSION_FRAMES);
        Training training = new Training(new Team());
        training.ball = new Ball(new SceneSettings());
        team.beforeTraining(training);
        check(team.getTeamPhase() == TeamPhase.DISPUTED,
            "beforeTraining clears phase inherited from a match");
        check(team.getTeamPhaseTracker().getPhaseFrames() == 0,
            "training begins before any phase frame has been sampled");
    }

    /** Phase must alter real target and decision outputs, not only expose a different enum value. */
    private static void phaseContextChangesProductionTacticalTrends() {
        TacticalState slow = slowState();
        PlayerTacticalInstruction neutral = PlayerTacticalInstruction.defaults();
        float transitionTarget = TacticalPositioning.targetY(
            0, -220, 0, 1, 0, -100, TeamPhase.ATTACKING_TRANSITION, slow, neutral);
        float organizedTarget = TacticalPositioning.targetY(
            0, -220, 0, 1, 0, -100, TeamPhase.ORGANIZED_POSSESSION, slow, neutral);
        check(organizedTarget > transitionTarget + 60,
            "slow tempo suppresses forward-run depth chiefly in organized possession");

        float transitionForwardScore = TacticalDecisionPolicy.passOptionScore(
            150, 150, 0, 0.8f, slow, neutral, TeamPhase.ATTACKING_TRANSITION);
        float organizedForwardScore = TacticalDecisionPolicy.passOptionScore(
            150, 150, 0, 0.8f, slow, neutral, TeamPhase.ORGANIZED_POSSESSION);
        check(transitionForwardScore > organizedForwardScore,
            "newly won possession retains more forward-pass value than settled slow build-up");
        check(TacticalDecisionPolicy.passingProbability(
                0.3f, slow, neutral, TeamPhase.ATTACKING_TRANSITION)
                > TacticalDecisionPolicy.passingProbability(
                    0.3f, slow, neutral, TeamPhase.ORGANIZED_POSSESSION),
            "newly won possession is less delayed by the settled retention profile");

        PlayerTacticalInstruction defendMore = PlayerTacticalInstruction.defendMore();
        float recoveryTarget = TacticalPositioning.targetY(
            200, -50, 0, 1, 260, 0, TeamPhase.DEFENDING_TRANSITION, slow, defendMore);
        float stableTarget = TacticalPositioning.targetY(
            200, -50, 0, 1, 260, 0, TeamPhase.STABLE_DEFENSE, slow, defendMore);
        check(recoveryTarget > stableTarget,
            "defend-more player recovers more goal-side immediately after losing possession");
        check(TacticalDecisionPolicy.effectiveDefenderDistance(
                200, defendMore, TeamPhase.DEFENDING_TRANSITION)
                < TacticalDecisionPolicy.effectiveDefenderDistance(
                    200, defendMore, TeamPhase.STABLE_DEFENSE),
            "defend-more player is a stronger pressure candidate during defensive transition");

        System.out.printf(Locale.ROOT,
            "Phase-aware trends: run target transition %.1f vs organized %.1f, "
                + "forward score %.2f vs %.2f, #7 recovery %.1f vs stable %.1f%n",
            transitionTarget, organizedTarget, transitionForwardScore, organizedForwardScore,
            recoveryTarget, stableTarget);
    }

    /** Default team and player values must keep the legacy branch and identical pure outputs. */
    private static void neutralTacticsPreserveLegacyDecisionPath() {
        TacticalState neutralState = new TacticalState();
        PlayerTacticalInstruction neutralPlayer = PlayerTacticalInstruction.defaults();
        check(!TacticalDecisionPolicy.usesTacticalPassing(
                neutralState, neutralPlayer, TeamPhase.ATTACKING_TRANSITION)
                && !TacticalDecisionPolicy.usesTacticalPassing(
                    neutralState, neutralPlayer, TeamPhase.ORGANIZED_POSSESSION),
            "neutral phase context retains legacy receiver search");
        close(TacticalDecisionPolicy.passingProbability(
                0.3f, neutralState, neutralPlayer, TeamPhase.ATTACKING_TRANSITION),
            0.3f, "neutral transition keeps legacy passing probability");
        close(TacticalDecisionPolicy.passingProbability(
                0.3f, neutralState, neutralPlayer, TeamPhase.ORGANIZED_POSSESSION),
            0.3f, "neutral settled phase keeps legacy passing probability");

        float transitionScore = TacticalDecisionPolicy.passOptionScore(
            150, 150, 0, 0.8f, neutralState, neutralPlayer,
            TeamPhase.ATTACKING_TRANSITION);
        float organizedScore = TacticalDecisionPolicy.passOptionScore(
            150, 150, 0, 0.8f, neutralState, neutralPlayer,
            TeamPhase.ORGANIZED_POSSESSION);
        close(transitionScore, organizedScore,
            "neutral phase context does not change pass scoring inputs");

        float transitionTarget = TacticalPositioning.targetY(
            0, -220, 0, 1, 0, 0, TeamPhase.ATTACKING_TRANSITION,
            neutralState, neutralPlayer);
        float organizedTarget = TacticalPositioning.targetY(
            0, -220, 0, 1, 0, 0, TeamPhase.ORGANIZED_POSSESSION,
            neutralState, neutralPlayer);
        close(transitionTarget, organizedTarget,
            "neutral phase context does not change formation target");
    }

    /** Telemetry must retain phase-scoped ratios and a complete snapshot for each committed pass. */
    private static void telemetryCapturesPhaseAndPassSnapshots() {
        Fixture fixture = new Fixture();
        TacticalDebugMetrics metrics = fixture.team.getTacticalDebugMetrics();
        TacticalState state = fixture.team.getTacticalState();
        state.setPossessionProfile(0.65f, 0.55f, 0.55f, 1.45f, 1.60f);
        converge(state);
        state.addPlayerInstruction(fixture.passer, PlayerTacticalInstruction.holdUpForward());

        sampleOwnership(fixture, metrics, TeamPhaseTracker.Ownership.TEAM,
            TeamPhaseTracker.SETTLED_POSSESSION_FRAMES);
        metrics.recordPass(fixture.team, fixture.passer, fixture.receiver, -90);
        metrics.recordPass(fixture.team, fixture.passer, fixture.receiver, 0);
        metrics.recordPass(fixture.team, fixture.passer, fixture.receiver, 90);

        check(metrics.getPassObservations().size() == 3,
            "every committed pass creates one telemetry observation");
        TacticalDebugMetrics.PassObservation observation = metrics.getPassObservations().get(0);
        check(observation.getPhase() == TeamPhase.ORGANIZED_POSSESSION
                && observation.getDirection() == TacticalDecisionPolicy.PassDirection.FORWARD,
            "pass observation retains phase and actual kick direction");
        close(observation.getDistance(), 100, "pass observation retains receiver distance");
        close(observation.getLaneSafety(), 1, "pass observation retains lane safety");
        close(observation.getTempo(), 0.65f, "pass observation snapshots team tempo");
        close(observation.getPassingRisk(), 0.55f,
            "pass observation snapshots team passing risk");
        close(observation.getHoldUpPlay(), 1,
            "pass observation snapshots passer-specific instruction");
        close(metrics.getPhasePassRatio(
                TeamPhase.ORGANIZED_POSSESSION,
                TacticalDecisionPolicy.PassDirection.FORWARD),
            1 / 3f, "organized-possession forward-pass ratio is phase scoped");

        metrics.startComparisonWindow();
        fixture.team.getTeamPhaseTracker().reset();
        sampleOwnership(fixture, metrics, TeamPhaseTracker.Ownership.TEAM,
            TeamPhaseTracker.OWNERSHIP_CONFIRMATION_FRAMES);
        metrics.recordPass(fixture.team, fixture.passer, fixture.receiver, -90);
        metrics.recordPass(fixture.team, fixture.passer, fixture.receiver, -90);
        metrics.recordPass(fixture.team, fixture.passer, fixture.receiver, 0);
        close(metrics.getPreviousPhasePassRatio(
                TeamPhase.ORGANIZED_POSSESSION,
                TacticalDecisionPolicy.PassDirection.FORWARD),
            1 / 3f, "comparison baseline retains organized phase ratios");
        close(metrics.getPhasePassRatio(
                TeamPhase.ATTACKING_TRANSITION,
                TacticalDecisionPolicy.PassDirection.FORWARD),
            2 / 3f, "current window reports attacking-transition ratios independently");
        check(metrics.getPreviousPhaseSeconds(TeamPhase.ORGANIZED_POSSESSION) > 0,
            "comparison baseline retains time spent in each phase");

        TacticalDebugMetrics jitterMetrics = new TacticalDebugMetrics();
        fixture.team.getTeamPhaseTracker().reset();
        sampleOwnership(fixture, jitterMetrics, TeamPhaseTracker.Ownership.TEAM,
            TeamPhaseTracker.OWNERSHIP_CONFIRMATION_FRAMES);
        sampleOwnership(fixture, jitterMetrics, TeamPhaseTracker.Ownership.OPPONENT,
            TeamPhaseTracker.OWNERSHIP_CONFIRMATION_FRAMES);
        check(jitterMetrics.getPhaseSwitches() == 2,
            "telemetry counts confirmed phase switches");
        check(jitterMetrics.getAbnormallyShortPhases() >= 1,
            "telemetry flags a completed abnormally short phase");
    }

    /** Identical seed and ownership input must reproduce both phase sequence and telemetry. */
    private static void fixedSeedScenarioIsRepeatable() {
        ScenarioResult first = runScenario(20260915L);
        ScenarioResult second = runScenario(20260915L);
        check(first.phaseSequence.equals(second.phaseSequence),
            "same fixed input reproduces every phase frame");
        check(first.telemetrySignature.equals(second.telemetrySignature),
            "same fixed seed and input reproduce phase telemetry");
        check(first.phaseSwitches == second.phaseSwitches,
            "same fixed input reproduces phase-switch count");
    }

    private static ScenarioResult runScenario(long seed) {
        Fixture fixture = new Fixture();
        TacticalDebugMetrics metrics = fixture.team.getTacticalDebugMetrics();
        Random random = new Random(seed);
        List<TeamPhase> sequence = new ArrayList<>();
        TeamPhaseTracker.Ownership[] ownership = {
            TeamPhaseTracker.Ownership.NONE,
            TeamPhaseTracker.Ownership.TEAM,
            TeamPhaseTracker.Ownership.OPPONENT,
            TeamPhaseTracker.Ownership.TEAM,
            TeamPhaseTracker.Ownership.NONE,
            TeamPhaseTracker.Ownership.OPPONENT
        };
        int[] durations = {4, 55, 2, 18, 10, 52};
        float[] passAngles = {-90, 0, 90};
        int frame = 0;
        for (int block = 0; block < ownership.length; block++) {
            for (int i = 0; i < durations[block]; i++) {
                fixture.team.getTeamPhaseTracker().update(ownership[block]);
                metrics.sample(fixture.team);
                sequence.add(fixture.team.getTeamPhase());
                if (frame % 23 == 7) {
                    metrics.recordPass(fixture.team, fixture.passer, fixture.receiver,
                        passAngles[random.nextInt(passAngles.length)]);
                }
                frame++;
            }
        }

        StringBuilder signature = new StringBuilder();
        for (TeamPhase phase : TeamPhase.values()) {
            signature.append(phase.ordinal()).append(':')
                .append(Float.floatToIntBits(metrics.getPhaseSeconds(phase))).append('/');
            for (TacticalDecisionPolicy.PassDirection direction
                 : TacticalDecisionPolicy.PassDirection.values()) {
                signature.append(metrics.getPhasePasses(phase, direction)).append(',');
            }
        }
        for (TacticalDebugMetrics.PassObservation pass : metrics.getPassObservations()) {
            signature.append('|').append(pass.getPhase().ordinal())
                .append(',').append(pass.getPhaseFrame())
                .append(',').append(Float.floatToIntBits(pass.getDistance()))
                .append(',').append(pass.getDirection().ordinal())
                .append(',').append(Float.floatToIntBits(pass.getLaneSafety()))
                .append(',').append(Float.floatToIntBits(pass.getWidth()))
                .append(',').append(Float.floatToIntBits(pass.getCompactness()))
                .append(',').append(Float.floatToIntBits(pass.getTempo()))
                .append(',').append(Float.floatToIntBits(pass.getPassingRisk()))
                .append(',').append(Float.floatToIntBits(pass.getForwardRunRate()))
                .append(',').append(Float.floatToIntBits(pass.getBallRetention()))
                .append(',').append(Float.floatToIntBits(pass.getBackPassPreference()))
                .append(',').append(Float.floatToIntBits(pass.getDefensiveWorkRate()))
                .append(',').append(Float.floatToIntBits(pass.getHoldUpPlay()))
                .append(',').append(Float.floatToIntBits(pass.getLayoffPreference()));
        }
        return new ScenarioResult(sequence, signature.toString(), metrics.getPhaseSwitches());
    }

    private static TacticalState slowState() {
        TacticalState state = new TacticalState();
        state.setPossessionProfile(0.65f, 0.55f, 0.55f, 1.45f, 1.60f);
        converge(state);
        return state;
    }

    private static TeamPhaseTracker settledTracker(TeamPhaseTracker.Ownership ownership) {
        TeamPhaseTracker tracker = new TeamPhaseTracker();
        repeat(tracker, ownership, TeamPhaseTracker.SETTLED_POSSESSION_FRAMES);
        return tracker;
    }

    private static void sampleOwnership(Fixture fixture, TacticalDebugMetrics metrics,
                                        TeamPhaseTracker.Ownership ownership, int frames) {
        for (int i = 0; i < frames; i++) {
            fixture.team.getTeamPhaseTracker().update(ownership);
            metrics.sample(fixture.team);
        }
    }

    private static void repeat(TeamPhaseTracker tracker,
                               TeamPhaseTracker.Ownership ownership, int frames) {
        for (int i = 0; i < frames; i++) tracker.update(ownership);
    }

    private static void converge(TacticalState state) {
        for (int i = 0; i < 400; i++) state.update();
    }

    private static Player preparedPlayer(Team team, Match match, float x, float y,
                                         int frameDistance) {
        Player player = new Player();
        player.team = team;
        player.scene = match;
        player.ball = match.ball;
        player.x = x;
        player.y = y;
        player.frameDistance = frameDistance;
        player.isActive = true;
        player.fsm = new PlayerFsm(player);
        player.fsm.setState(PlayerFsm.Id.STATE_STAND_RUN);
        team.lineup.add(player);
        return player;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void close(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < EPSILON,
            message + ": expected " + expected + ", got " + actual);
    }

    /** Minimal real match objects needed by positioning and passive pass telemetry. */
    private static final class Fixture {
        final Match match = new Match();
        final Team team = new Team();
        final Player passer;
        final Player receiver;

        Fixture() {
            match.ball = new Ball(new SceneSettings());
            team.match = match;
            team.index = Match.HOME;
            team.side = 1;
            team.lineup = new ArrayList<>();
            match.team[Match.HOME] = team;
            passer = player(0, 0);
            receiver = player(0, -100);
            match.ball.owner = passer;
        }

        private Player player(float x, float y) {
            Player player = new Player();
            player.team = team;
            player.scene = match;
            player.ball = match.ball;
            player.x = x;
            player.y = y;
            player.tx = x;
            player.ty = y;
            player.isActive = true;
            player.role = Player.Role.MIDFIELDER;
            team.lineup.add(player);
            return player;
        }
    }

    private static final class ScenarioResult {
        final List<TeamPhase> phaseSequence;
        final String telemetrySignature;
        final int phaseSwitches;

        ScenarioResult(List<TeamPhase> phaseSequence, String telemetrySignature,
                       int phaseSwitches) {
            this.phaseSequence = phaseSequence;
            this.telemetrySignature = telemetrySignature;
            this.phaseSwitches = phaseSwitches;
        }
    }
}
