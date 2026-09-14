package com.ygames.ysoccer.match;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.ygames.ysoccer.framework.Assets;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Deterministic behavior-level regression checks for the first tactical-control demo.
 *
 * <p>The checks execute the same positioning and decision policies used by the match AI. They
 * compare neutral and instructed outcomes rather than merely asserting that state fields changed,
 * and require neither a graphics context nor randomized match simulation.</p>
 */
public final class TacticalStateRegressionTest {

    private static final float EPSILON = 0.001f;
    private static int checks;

    /** Runs all tactical trend checks and exits nonzero when a coach intent has no AI effect. */
    public static void main(String[] args) {
        shapeTransitionsAndContracts();
        formationTargetsFlowThroughTeamTacticalUpdate();
        holdUpForwardChangesSupportAndPossessionChoices();
        individualDefensiveWorkChangesRecoveryAndPressureEligibility();
        slowTempoChangesPassingAndForwardRuns();
        tacticalPassingSelectsAConservativeReceiver();
        telemetryRecordsExecutedBehavior();
        playerInstructionsRemainIndividualAndResettable();
        System.out.println("Tactical state regression checks passed: " + checks);
    }

    /** Compact shape must converge gradually and reduce both width and line separation. */
    private static void shapeTransitionsAndContracts() {
        TacticalState defaultState = new TacticalState();
        TacticalState compactState = new TacticalState();
        compactState.setShape(0.72f, 1.35f);

        close(compactState.getWidth(), 1, "shape request does not jump current width");
        compactState.update();
        check(compactState.getWidth() < 1 && compactState.getWidth() > 0.72f,
            "width begins a gradual transition");
        converge(compactState);

        float[] baseX = {-320, -190, 190, 320, -230, 0, 230, -120, 0, 120};
        float[] baseY = {230, 230, 230, 230, 0, 0, 0, -220, -220, -220};
        float defaultWidth = averageHorizontalDistance(baseX, baseY, defaultState);
        float compactWidth = averageHorizontalDistance(baseX, baseY, compactState);
        float defaultLines = lineSeparation(baseX, baseY, defaultState);
        float compactLines = lineSeparation(baseX, baseY, compactState);

        check(compactWidth < 0.8f * defaultWidth,
            "compact instruction significantly reduces average horizontal distance");
        check(compactLines < 0.8f * defaultLines,
            "compact instruction significantly reduces defender/midfielder/attacker separation");

        compactState.resetShape();
        check(compactState.getWidth() < 1, "shape reset is also gradual");
        converge(compactState);
        close(compactState.getWidth(), TacticalState.DEFAULT_WIDTH, "width returns to default");
        close(compactState.getCompactness(), TacticalState.DEFAULT_COMPACTNESS,
            "compactness returns to default");

        System.out.printf(Locale.ROOT,
            "Shape metrics: horizontal %.1f -> %.1f, line separation %.1f -> %.1f%n",
            defaultWidth, compactWidth, defaultLines, compactLines);
    }

    /** The production Team path must actually publish compacted targets to active players. */
    private static void formationTargetsFlowThroughTeamTacticalUpdate() {
        Files previousFiles = Gdx.files;
        if (Gdx.files == null) Gdx.files = new TestFiles();
        Tactics previous = Assets.tactics[0];
        try {
            Tactics tactics = new Tactics();
            Assets.tactics[0] = tactics;
            Match match = new Match();
            match.ball = new Ball(new SceneSettings());
            Team team = new Team();
            team.match = match;
            team.index = Match.HOME;
            team.side = 1;
            team.tactics = 0;
            team.lineup = new ArrayList<>();
            match.team[Match.HOME] = team;

            for (int i = 0; i < Const.TEAM_SIZE; i++) {
                Player player = new Player();
                player.team = team;
                team.lineup.add(player);
                if (i > 0) {
                    tactics.target[i][17][0] = -300 + 60 * (i - 1);
                    tactics.target[i][17][1] = i <= 4 ? 220 : (i <= 7 ? 0 : -220);
                }
            }

            team.updateTactics(true);
            float normalWidth = averagePublishedWidth(team);
            team.getTacticalState().setShape(0.72f, 1.35f);
            converge(team.getTacticalState());
            team.updateTactics(true);
            float compactWidth = averagePublishedWidth(team);

            check(normalWidth > 0, "Team publishes formation targets to active players");
            check(compactWidth < 0.8f * normalWidth,
                "Team.updateTactics applies the production compact-shape path");
        } finally {
            Assets.tactics[0] = previous;
            Gdx.files = previousFiles;
        }
    }

    /** Hold-up intent must deepen the forward target and favor nearby teammates over direct goal play. */
    private static void holdUpForwardChangesSupportAndPossessionChoices() {
        TacticalState state = new TacticalState();
        PlayerTacticalInstruction neutral = PlayerTacticalInstruction.defaults();
        PlayerTacticalInstruction holdUp = PlayerTacticalInstruction.holdUpForward();
        float neutralTarget = TacticalPositioning.targetY(
            0, -220, 0, 1, 0, -150, TeamPhase.ORGANIZED_POSSESSION, state, neutral);
        float holdUpTarget = TacticalPositioning.targetY(
            0, -220, 0, 1, 0, -150, TeamPhase.ORGANIZED_POSSESSION, state, holdUp);

        check(holdUpTarget > neutralTarget + 80,
            "hold-up forward target moves substantially closer to midfield");
        check(TacticalDecisionPolicy.mateInfluence(
                state, holdUp, TeamPhase.ORGANIZED_POSSESSION)
                > TacticalDecisionPolicy.mateInfluence(
                    state, neutral, TeamPhase.ORGANIZED_POSSESSION),
            "hold-up play raises teammate steering influence");
        check(TacticalDecisionPolicy.goalInfluence(
                state, holdUp, TeamPhase.ORGANIZED_POSSESSION)
                < TacticalDecisionPolicy.goalInfluence(
                    state, neutral, TeamPhase.ORGANIZED_POSSESSION),
            "hold-up play reduces immediate goal steering");
        check(TacticalDecisionPolicy.passingProbability(
                0.3f, state, holdUp, TeamPhase.ORGANIZED_POSSESSION)
                > TacticalDecisionPolicy.passingProbability(
                    0.3f, state, neutral, TeamPhase.ORGANIZED_POSSESSION),
            "layoff preference raises the eligible passing decision probability");
        check(TacticalDecisionPolicy.shootingProbability(
                0.5f, state, holdUp, TeamPhase.ORGANIZED_POSSESSION)
                < TacticalDecisionPolicy.shootingProbability(
                    0.5f, state, neutral, TeamPhase.ORGANIZED_POSSESSION),
            "hold-up play reduces rushed direct-shot decisions");
        check(holdUp.getComeShort() > 0 && holdUp.getRunBehind() < 0,
            "hold-up preset combines come-short and reduced run-behind preferences");

        System.out.printf(Locale.ROOT,
            "Hold-up target: %.1f -> %.1f, pass probability %.3f -> %.3f%n",
            neutralTarget, holdUpTarget,
            TacticalDecisionPolicy.passingProbability(
                0.3f, state, neutral, TeamPhase.ORGANIZED_POSSESSION),
            TacticalDecisionPolicy.passingProbability(
                0.3f, state, holdUp, TeamPhase.ORGANIZED_POSSESSION));
    }

    /** #7's instruction must affect only his defensive target, reaction, and pressure eligibility. */
    private static void individualDefensiveWorkChangesRecoveryAndPressureEligibility() {
        TacticalState state = new TacticalState();
        PlayerTacticalInstruction neutral = PlayerTacticalInstruction.defaults();
        PlayerTacticalInstruction defendMore = PlayerTacticalInstruction.defendMore();
        float neutralX = TacticalPositioning.targetX(
            200, 0, 260, TeamPhase.STABLE_DEFENSE, state, neutral);
        float supportX = TacticalPositioning.targetX(
            200, 0, 260, TeamPhase.STABLE_DEFENSE, state, defendMore);
        float neutralY = TacticalPositioning.targetY(
            200, -50, 0, 1, 260, 0, TeamPhase.STABLE_DEFENSE, state, neutral);
        float defensiveY = TacticalPositioning.targetY(
            200, -50, 0, 1, 260, 0, TeamPhase.STABLE_DEFENSE, state, defendMore);

        check(defensiveY > neutralY + 80,
            "defend-more target recovers closer to the own goal");
        check(Math.abs(260 - supportX) < Math.abs(260 - neutralX),
            "same-flank support target shifts toward the opponent's attack");
        check(TacticalDecisionPolicy.effectiveDefenderDistance(
                200, defendMore, TeamPhase.STABLE_DEFENSE)
                < TacticalDecisionPolicy.effectiveDefenderDistance(
                    200, neutral, TeamPhase.STABLE_DEFENSE),
            "defend-more player is more eligible for primary pressure");
        check(TacticalDecisionPolicy.defendingUpdateInterval(
                24, defendMore, TeamPhase.STABLE_DEFENSE)
                < TacticalDecisionPolicy.defendingUpdateInterval(
                    24, neutral, TeamPhase.STABLE_DEFENSE),
            "defend-more player refreshes chase direction more often");

        System.out.printf(Locale.ROOT,
            "#7 defense target Y: %.1f -> %.1f, effective pressure distance %.1f -> %.1f%n",
            neutralY, defensiveY,
            TacticalDecisionPolicy.effectiveDefenderDistance(
                200, neutral, TeamPhase.STABLE_DEFENSE),
            TacticalDecisionPolicy.effectiveDefenderDistance(
                200, defendMore, TeamPhase.STABLE_DEFENSE));
    }

    /** Slow tempo must reduce progression without altering physical movement-speed fields. */
    private static void slowTempoChangesPassingAndForwardRuns() {
        TacticalState normal = new TacticalState();
        TacticalState slow = new TacticalState();
        slow.setPossessionProfile(0.65f, 0.55f, 0.55f, 1.45f, 1.60f);
        converge(slow);
        PlayerTacticalInstruction neutral = PlayerTacticalInstruction.defaults();

        float normalRunTarget = TacticalPositioning.targetY(
            0, -220, 0, 1, 0, -100, TeamPhase.ORGANIZED_POSSESSION, normal, neutral);
        float slowRunTarget = TacticalPositioning.targetY(
            0, -220, 0, 1, 0, -100, TeamPhase.ORGANIZED_POSSESSION, slow, neutral);
        check(slowRunTarget > normalRunTarget + 80,
            "slow tempo reduces the depth of advanced support runs");

        float normalForward = passScore(150, normal);
        float slowForward = passScore(150, slow);
        float normalSide = passScore(0, normal);
        float slowSide = passScore(0, slow);
        float normalBack = passScore(-150, normal);
        float slowBack = passScore(-150, slow);
        check(slowForward < normalForward, "slow tempo lowers forward-pass score");
        check(slowSide > normalSide, "slow tempo raises sideways-pass score");
        check(slowBack > normalBack, "slow tempo raises backward-pass score");
        check(TacticalDecisionPolicy.passingProbability(
                0.3f, slow, neutral, TeamPhase.ORGANIZED_POSSESSION)
                < TacticalDecisionPolicy.passingProbability(
                    0.3f, normal, neutral, TeamPhase.ORGANIZED_POSSESSION),
            "slow tempo increases possession time between eligible pass decisions");
        check(TacticalDecisionPolicy.shootingProbability(
                0.5f, slow, neutral, TeamPhase.ORGANIZED_POSSESSION)
                < TacticalDecisionPolicy.shootingProbability(
                    0.5f, normal, neutral, TeamPhase.ORGANIZED_POSSESSION),
            "slow tempo does not turn lower pass frequency into more rushed shots");

        System.out.printf(Locale.ROOT,
            "Pass scores forward %.2f -> %.2f, side %.2f -> %.2f, back %.2f -> %.2f%n",
            normalForward, slowForward, normalSide, slowSide, normalBack, slowBack);
    }

    /** The Player-level receiver search must turn conservative scores into an actual selection. */
    private static void tacticalPassingSelectsAConservativeReceiver() {
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
        for (int i = 0; i < TeamPhaseTracker.SETTLED_POSSESSION_FRAMES; i++) {
            team.getTeamPhaseTracker().update(TeamPhaseTracker.Ownership.TEAM);
        }

        carrier.searchTacticalPassingMate();
        check(carrier.passingMate == forward,
            "neutral state preserves the original forward narrow-cone receiver search");

        team.getTacticalState().setPossessionProfile(0.65f, 0.55f, 0.55f, 1.45f, 1.60f);
        converge(team.getTacticalState());
        carrier.searchTacticalPassingMate();
        check(carrier.passingMate == backward,
            "slow-tempo Player search selects the highest-scoring backward outlet");
    }

    /** Passive telemetry must classify committed pass angles and retain the possession window. */
    private static void telemetryRecordsExecutedBehavior() {
        Match match = new Match();
        match.ball = new Ball(new SceneSettings());
        Team team = new Team();
        team.match = match;
        team.index = Match.HOME;
        team.side = 1;
        team.lineup = new ArrayList<>();
        match.team[Match.HOME] = team;
        Player carrier = preparedPlayer(team, match, 0, 0, 1);
        carrier.role = Player.Role.MIDFIELDER;
        for (int i = 1; i < Const.TEAM_SIZE; i++) {
            Player player = preparedPlayer(team, match, i * 20, i * 30, i);
            player.role = i <= 4
                ? Player.Role.DEFENDER
                : (i <= 8 ? Player.Role.MIDFIELDER : Player.Role.ATTACKER);
        }
        match.ball.owner = carrier;

        TacticalDebugMetrics metrics = team.getTacticalDebugMetrics();
        for (int i = 0; i < 64; i++) {
            team.getTeamPhaseTracker().update(TeamPhaseTracker.Ownership.TEAM);
            metrics.sample(team);
        }
        metrics.recordPass(team, carrier, null, -90);
        metrics.recordPass(team, carrier, null, 0);
        metrics.recordPass(team, carrier, null, 90);

        check(metrics.getForwardPasses() == 1, "executed forward pass is classified");
        check(metrics.getSidewaysPasses() == 1, "executed sideways pass is classified");
        check(metrics.getBackwardPasses() == 1, "executed backward pass is classified");
        close(metrics.getAveragePossessionSecondsAtPass(), 1,
            "pass telemetry records the preceding possession window");
        metrics.startComparisonWindow();
        check(metrics.hasPreviousWindow() && metrics.getPreviousForwardPasses() == 1
                && metrics.getPreviousSidewaysPasses() == 1
                && metrics.getPreviousBackwardPasses() == 1,
            "new tactical window preserves executed-pass baseline");
        check(metrics.getForwardPasses() == 0 && metrics.getObservationSeconds() == 0,
            "new tactical window starts with fresh current observations");
        metrics.reset();
        check(!metrics.hasPreviousWindow() && metrics.getForwardPasses() == 0,
            "new-match telemetry reset clears current and previous windows");
    }

    /** A shirt-number instruction must not leak to teammates and full reset must remove it. */
    private static void playerInstructionsRemainIndividualAndResettable() {
        TacticalState state = new TacticalState();
        Player playerSeven = new Player();
        Player teammate = new Player();
        state.addPlayerInstruction(playerSeven, PlayerTacticalInstruction.defendMore());

        check(state.getInstruction(playerSeven).getDefensiveWorkRate() > 0,
            "selected player receives individual instruction");
        check(state.getInstruction(teammate).isDefault(),
            "individual instruction does not affect a teammate");
        state.resetAll();
        check(state.getInstruction(playerSeven).isDefault(),
            "full tactical reset removes individual instruction");
    }

    private static float averageHorizontalDistance(float[] baseX, float[] baseY,
                                                   TacticalState state) {
        float total = 0;
        for (int i = 0; i < baseX.length; i++) {
            float target = TacticalPositioning.targetX(
                baseX[i], 0, 0, TeamPhase.DISPUTED,
                state, PlayerTacticalInstruction.defaults());
            total += Math.abs(target);
        }
        return total / baseX.length;
    }

    private static float lineSeparation(float[] baseX, float[] baseY, TacticalState state) {
        float defenders = lineMean(baseX, baseY, state, 0, 4);
        float midfielders = lineMean(baseX, baseY, state, 4, 7);
        float attackers = lineMean(baseX, baseY, state, 7, 10);
        return Math.abs(defenders - midfielders) + Math.abs(midfielders - attackers);
    }

    private static float lineMean(float[] baseX, float[] baseY, TacticalState state,
                                  int from, int to) {
        float total = 0;
        for (int i = from; i < to; i++) {
            total += TacticalPositioning.targetY(
                baseX[i], baseY[i], 0, 1, 0, 0, TeamPhase.DISPUTED,
                state, PlayerTacticalInstruction.defaults());
        }
        return total / (to - from);
    }

    private static float passScore(float attackingProgress, TacticalState state) {
        return TacticalDecisionPolicy.passOptionScore(
            attackingProgress, 150, 0, 0.8f, state,
            PlayerTacticalInstruction.defaults(), TeamPhase.ORGANIZED_POSSESSION);
    }

    private static float averagePublishedWidth(Team team) {
        float centre = 0;
        for (int i = 1; i < Const.TEAM_SIZE; i++) centre += team.lineup.get(i).tx;
        centre /= Const.TEAM_SIZE - 1;

        float total = 0;
        for (int i = 1; i < Const.TEAM_SIZE; i++) {
            total += Math.abs(team.lineup.get(i).tx - centre);
        }
        return total / (Const.TEAM_SIZE - 1);
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

    private static void converge(TacticalState state) {
        for (int i = 0; i < 400; i++) state.update();
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void close(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < EPSILON,
            message + ": expected " + expected + ", got " + actual);
    }

    /** Minimal file service needed to initialize Assets without starting a libGDX application. */
    private static final class TestFiles implements Files {
        @Override
        public FileHandle getFileHandle(String path, FileType type) {
            return new FileHandle(path);
        }

        @Override
        public FileHandle classpath(String path) {
            return new FileHandle(path);
        }

        @Override
        public FileHandle internal(String path) {
            return new FileHandle(path);
        }

        @Override
        public FileHandle external(String path) {
            return new FileHandle(path);
        }

        @Override
        public FileHandle absolute(String path) {
            return new FileHandle(path);
        }

        @Override
        public FileHandle local(String path) {
            return new FileHandle(path);
        }

        @Override
        public String getExternalStoragePath() {
            return "";
        }

        @Override
        public boolean isExternalStorageAvailable() {
            return false;
        }

        @Override
        public String getLocalStoragePath() {
            return "";
        }

        @Override
        public boolean isLocalStorageAvailable() {
            return true;
        }
    }
}
