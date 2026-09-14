package com.ygames.ysoccer.match;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.ygames.ysoccer.framework.Assets;
import com.ygames.ysoccer.framework.Font;
import com.ygames.ysoccer.framework.GLColor;
import com.ygames.ysoccer.framework.GLGame;
import com.ygames.ysoccer.framework.GLGraphics;
import com.ygames.ysoccer.framework.Settings;

import java.util.Locale;

import static com.badlogic.gdx.Gdx.gl;
import static com.ygames.ysoccer.framework.Assets.gettext;
import static com.ygames.ysoccer.framework.Font.Align.CENTER;
import static com.ygames.ysoccer.match.Const.BALL_ZONE_DX;
import static com.ygames.ysoccer.match.Const.BALL_ZONE_DY;
import static com.ygames.ysoccer.match.Const.SECOND;
import static com.ygames.ysoccer.match.Const.TEAM_SIZE;
import static com.ygames.ysoccer.match.Const.TOUCH_LINE;
import static com.ygames.ysoccer.match.Match.AWAY;
import static com.ygames.ysoccer.match.Match.HOME;
import static com.ygames.ysoccer.match.PlayerFsm.Id.STATE_RED_CARD;
import static com.ygames.ysoccer.match.PlayerFsm.Id.STATE_SENT_OFF;
import static com.ygames.ysoccer.match.PlayerFsm.Id.STATE_SUBSTITUTED;
import static java.lang.Math.min;

public class MatchRenderer extends SceneRenderer<Match> {

    private MatchState matchState;
    private final BallSprite ballSprite;

    public MatchRenderer(GLGraphics glGraphics, Match match) {
        super(glGraphics, match);
        this.ball = match.ball;

        this.hotKeys = new MatchHotKeys(match, this);

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        match.camera.x = 0.5f * (Const.PITCH_W - match.camera.getWorldViewportWidth());
        match.camera.y = 0;
        for (int i = 0; i < Const.REPLAY_SUBFRAMES; i++) {
            match.vCameraX[i] = Math.round(match.camera.x);
            match.vCameraY[i] = Math.round(match.camera.y);
        }

        ballSprite = new BallSprite(glGraphics, match.ball, viewTransform);
        allSprites.add(ballSprite);
        for (int t = HOME; t <= AWAY; t++) {
            CoachSprite coachSprite = new CoachSprite(glGraphics, match.team[t].coach, viewTransform);
            allSprites.add(coachSprite);
            int len = match.team[t].lineup.size();
            for (int i = 0; i < len; i++) {
                PlayerSprite playerSprite = new PlayerSprite(glGraphics, match.team[t].lineup.get(i), viewTransform);
                allSprites.add(playerSprite);
            }
        }

        for (int xSide = -1; xSide <= 1; xSide += 2) {
            for (int ySide = -1; ySide <= 1; ySide += 2) {
                allSprites.add(new JumperSprite(glGraphics, xSide, ySide, viewTransform));
            }
        }

        cornerFlagSprites = new CornerFlagSprite[4];
        for (int i = 0; i < 4; i++) {
            cornerFlagSprites[i] = new CornerFlagSprite(
                glGraphics, scene.settings, i / 2 * 2 - 1, i % 2 * 2 - 1, viewTransform
            );
            allSprites.add(cornerFlagSprites[i]);
        }
        if (!viewTransform.isHorizontal()) {
            allSprites.add(new GoalTopA(glGraphics, viewTransform));
            allSprites.add(new GoalTopB(glGraphics, viewTransform));
        }

        Assets.crowdRenderer.setMaxRank(match.rank);
    }

    public void render() {
        matchState = scene.getState();

        glGraphics.light = scene.light;

        gl.glEnable(GL20.GL_BLEND);
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        configureWorldCamera();
        batch.begin();

        beginGroundTransform();
        renderBackground();
        endGroundTransform();

        if (Settings.showDevelopmentInfo && Settings.showBallZones) {
            drawBallZones();
        }

        if (Settings.showDevelopmentInfo && Settings.showBallPredictions) {
            drawBallPredictions(ball);
        }

        Assets.crowdRenderer.draw(batch, viewTransform);

        if (viewTransform.isHorizontal()) {
            drawHorizontalGoalBacks();
        }

        renderSprites();

        if (viewTransform.isHorizontal()) {
            drawHorizontalGoalFronts();
            redrawBallShadowsOverGoals(scene.ball);
            redrawBallOverHorizontalGoals(ballSprite);
        } else {
            redrawBallShadowsOverGoals(scene.ball);
            redrawBallOverTopGoal(ballSprite);

            // redraw bottom goal
            batch.draw(Assets.goalBottom, Const.GOAL_BTM_X, Const.GOAL_BTM_Y, 146, 56, 0, 0, 146, 56, false, true);

            redrawBallShadowsOverGoals(scene.ball);
            redrawBallOverBottomGoal(ballSprite);
        }

        if (scene.settings.weatherStrength != Weather.Strength.NONE) {
            switch (scene.settings.weatherEffect) {
                case Weather.RAIN:
                    drawRain();
                    break;

                case Weather.SNOW:
                    drawSnow();
                    break;

                case Weather.FOG:
                    drawFog();
                    break;
            }
        }

        if (scene.displayControlledPlayer) {
            drawControlledPlayersNumbers();
        }

        if (scene.displayFoulMaker) {
            Player player = scene.foul.player;
            if (scene.displayRedCard) {
                drawRedCard(player);
            } else if (scene.displayYellowCard) {
                drawYellowCard(player);
            } else {
                drawPlayerNumber(scene.foul.player);
            }
        }

        batch.end();

        renderGui();
    }

    private void renderGui() {
        camera.setToOrtho(true, guiWidth, guiHeight);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setColor(0xFFFFFF, guiAlpha);

        // ball owner
        if (scene.displayBallOwner && scene.ball.ownerIndex != -1) {
            drawPlayerNumberAndName(scene.team[scene.ball.ownerTeamIndex].lineup.get(scene.ball.ownerIndex));
        }

        // foul maker
        if (scene.displayFoulMaker) {
            drawPlayerNumberAndName(scene.foul.player);
        }

        if (Settings.showDevelopmentInfo) {
            Assets.font10.draw(batch, scene.camera.getMode() +
                    ", " + scene.camera.getSpeed() +
                    ", " + scene.camera.limitedToString() +
                    ", " + scene.camera.targetToString() +
                    ", " + scene.camera.offsetToString()
                    , guiWidth / 5, 42, Font.Align.LEFT);
            drawTacticalStateDebug();
        }

        // clock
        if (scene.displayTime) {
            drawTime();
        }

        // radar
        if (scene.displayRadar && scene.getSettings().radar) {
            drawRadar();
        }

        // wind vane
        if (scene.displayWindVane && (scene.settings.wind.speed > 0)) {
            batch.draw(Assets.wind[scene.settings.wind.direction][scene.settings.wind.speed - 1], guiWidth - 50, 20);
        }

        // rosters
        if (scene.displayRosters) {
            drawRosters();
        }

        // score
        if (scene.displayScore) {
            drawScore();
        }

        // penalties score
        if (scene.displayPenaltiesScore) {
            drawPenaltiesScore();
        }

        // messages
        if (hotKeys.messageTimer > 0) {
            batch.setColor(0xFFFFFF, guiAlpha);
            Assets.font10.draw(batch, hotKeys.message, guiWidth / 2, 1, Font.Align.CENTER);
        }

        // statistics
        if (scene.displayStatistics) {
            drawStatistics();
        }

        // goal scorer
        if (scene.displayGoalScorer && (scene.subframe % 160 > 80)) {
            drawPlayerNumberAndName(scene.ball.goalOwner);
        }

        if (scene.displayBenchPlayers) {
            drawBenchPlayers();
        }

        if (scene.displayBenchFormation) {
            drawBenchFormation();
        }

        if (scene.displayTacticsSwitch) {
            drawTacticsSwitch();
        }

        if (scene.displayHelp) {
            drawHelp(hotKeys.keyMap);
        }

        if (scene.displayPause) {
            Assets.font10.draw(batch, gettext("PAUSE"), guiWidth / 2, 22, Font.Align.CENTER);
        }

        if (scene.displayReplayGui) {
            drawReplayGui();
        }

        if (scene.displayHighlightsGui) {
            drawHighlightsGui();
        }

        if (scene.displayReplayControls) {
            int frameX = 1 + matchState.inputDevice.x1;
            int frameY = 1 + matchState.inputDevice.y1;
            batch.draw(Assets.replaySpeed[frameX][frameY], guiWidth - 50, guiHeight - 50);
        }

        batch.end();
    }

    /** Draws the current team and individual coach biases used by the real match AI. */
    private void drawTacticalStateDebug() {
        Team team = scene.tacticalDebugTeam();
        if (team == null || team.lineup == null) return;

        TacticalState state = team.getTacticalState();
        TacticalDebugMetrics metrics = team.getTacticalDebugMetrics();
        TeamPhase phase = team.getTeamPhase();
        String side = team.index == HOME ? "HOME" : "AWAY";
        Assets.font10.draw(batch, String.format(Locale.ROOT,
            "TacticalState [%s] | phase %s %.1fs",
            side, phase, team.getTeamPhaseFrames() / (float) GLGame.VIRTUAL_REFRESH_RATE),
            5, 62, Font.Align.LEFT);
        Assets.font10.draw(batch, String.format(Locale.ROOT,
            "width %.2f -> %.2f | compactness %.2f -> %.2f",
            state.getWidth(), state.getTargetWidth(),
            state.getCompactness(), state.getTargetCompactness()),
            5, 82, Font.Align.LEFT);
        Assets.font10.draw(batch, String.format(Locale.ROOT,
            "tempo %.2f | passingRisk %.2f | forwardRun %.2f | retention %.2f | backPass %.2f",
            state.getTempo(), state.getPassingRisk(), state.getForwardRunRate(),
            state.getBallRetention(), state.getBackPassPreference()),
            5, 102, Font.Align.LEFT);

        if (metrics.hasPreviousWindow()) {
            Assets.font10.draw(batch, String.format(Locale.ROOT,
                "actualSpread %.1f -> %.1f | lineGap %.1f -> %.1f | forwardRun %.0f%% -> %.0f%%",
                metrics.getPreviousHorizontalSpread(), metrics.getAverageHorizontalSpread(),
                metrics.getPreviousLineGap(), metrics.getAverageLineGap(),
                100 * metrics.getPreviousForwardRunRatio(), 100 * metrics.getForwardRunRatio()),
                5, 122, Font.Align.LEFT);
        } else {
            Assets.font10.draw(batch, String.format(Locale.ROOT,
                "observed %.1fs | actualSpread %.1f | lineGap %.1f | forwardRun %.0f%%",
                metrics.getObservationSeconds(), metrics.getAverageHorizontalSpread(),
                metrics.getAverageLineGap(), 100 * metrics.getForwardRunRatio()),
                5, 122, Font.Align.LEFT);
        }

        float transitionSeconds = metrics.getPhaseSeconds(TeamPhase.ATTACKING_TRANSITION)
            + metrics.getPhaseSeconds(TeamPhase.DEFENDING_TRANSITION);
        Assets.font10.draw(batch, String.format(Locale.ROOT,
            "phase seconds O/T/D/U %.1f/%.1f/%.1f/%.1f | switches/short %d/%d",
            metrics.getPhaseSeconds(TeamPhase.ORGANIZED_POSSESSION), transitionSeconds,
            metrics.getPhaseSeconds(TeamPhase.STABLE_DEFENSE),
            metrics.getPhaseSeconds(TeamPhase.DISPUTED), metrics.getPhaseSwitches(),
            metrics.getAbnormallyShortPhases()),
            5, 142, Font.Align.LEFT);

        float previousForward = metrics.getPreviousPhasePassRatio(
            phase, TacticalDecisionPolicy.PassDirection.FORWARD);
        float previousSide = metrics.getPreviousPhasePassRatio(
            phase, TacticalDecisionPolicy.PassDirection.SIDEWAYS);
        float previousBack = metrics.getPreviousPhasePassRatio(
            phase, TacticalDecisionPolicy.PassDirection.BACKWARD);
        Assets.font10.draw(batch, String.format(Locale.ROOT,
            "phase pass%% F/S/B %.0f/%.0f/%.0f -> %.0f/%.0f/%.0f",
            100 * previousForward, 100 * previousSide, 100 * previousBack,
            100 * metrics.getPhasePassRatio(phase, TacticalDecisionPolicy.PassDirection.FORWARD),
            100 * metrics.getPhasePassRatio(phase, TacticalDecisionPolicy.PassDirection.SIDEWAYS),
            100 * metrics.getPhasePassRatio(phase, TacticalDecisionPolicy.PassDirection.BACKWARD)),
            5, 162, Font.Align.LEFT);

        Player forward = team.findPrimaryAttacker();
        if (forward != null) {
            PlayerTacticalInstruction instruction = state.getInstruction(forward);
            Assets.font10.draw(batch, String.format(Locale.ROOT,
                "Player #%d holdUp %.2f | comeShort %.2f | runBehind %.2f | layoff %.2f",
                forward.number, instruction.getHoldUpPlay(), instruction.getComeShort(),
                instruction.getRunBehind(), instruction.getLayoffPreference()),
                5, 182, Font.Align.LEFT);
        }

        Player playerSeven = team.findPlayerByNumber(7);
        if (playerSeven != null) {
            PlayerTacticalInstruction instruction = state.getInstruction(playerSeven);
            Assets.font10.draw(batch, String.format(Locale.ROOT,
                "Player #7 defensiveWork %.2f | tracking %.2f | depth %.2f | pressSupport %.2f",
                instruction.getDefensiveWorkRate(), instruction.getTrackingBack(),
                instruction.getDefensiveDepth(), instruction.getPressSupport()),
                5, 202, Font.Align.LEFT);
        }
    }

    private void renderBackground() {
        batch.disableBlending();
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                batch.draw(Assets.stadium[r][c], -Const.CENTER_X + 512 * c, -Const.CENTER_Y + 512 * r);
            }
        }
        batch.enableBlending();
    }

    private void drawBallZones() {
        batch.end();
        shapeRenderer.setAutoShapeType(true);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin();
        shapeRenderer.setColor(0xAAAA00, 0.4f);
        for (int x = 0; x < 6; x++) {
            float worldX = (-2.5f + x) * BALL_ZONE_DX;
            shapeRenderer.line(
                viewTransform.projectX(worldX, -3.5f * BALL_ZONE_DY),
                viewTransform.groundDepth(worldX, -3.5f * BALL_ZONE_DY),
                viewTransform.projectX(worldX, 3.5f * BALL_ZONE_DY),
                viewTransform.groundDepth(worldX, 3.5f * BALL_ZONE_DY)
            );
        }
        for (int y = 0; y < 8; y++) {
            float worldY = (-3.5f + y) * BALL_ZONE_DY;
            shapeRenderer.line(
                viewTransform.projectX(-2.5f * BALL_ZONE_DX, worldY),
                viewTransform.groundDepth(-2.5f * BALL_ZONE_DX, worldY),
                viewTransform.projectX(2.5f * BALL_ZONE_DX, worldY),
                viewTransform.groundDepth(2.5f * BALL_ZONE_DX, worldY)
            );
        }
        shapeRenderer.end();
        batch.begin();
    }

    @Override
    protected void drawShadows() {
        batch.setColor(0xFFFFFF, scene.settings.shadowAlpha);

        drawBallShadow(scene.ball, false);

        for (int i = 0; i < 4; i++) {
            cornerFlagSprites[i].drawShadow(scene.subframe, batch);
        }

        // keepers
        for (int t = HOME; t <= AWAY; t++) {
            for (Player player : scene.team[t].lineup) {
                if (player.role == Player.Role.GOALKEEPER) {
                    FrameData d = player.currentData;
                    if (d.isVisible) {
                        Integer[] origin = Assets.keeperOrigins[d.fmy][d.fmx];
                        batch.draw(
                            Assets.keeperShadow[d.fmx][d.fmy][0],
                            d.x - origin[0] + 0.65f * d.z,
                            d.y - origin[1] + 0.46f * d.z
                        );
                        // TODO activate after getting keeper shadows
                        // if (scene.settings.time == MatchSettings.Time.NIGHT) {
                        // batch.draw(Assets.keeperShadow[d.fmx][d.fmy][1], d.x - 24 - 0.65f * d.z, d.y - 34 + 0.46f * d.z);
                        // batch.draw(Assets.keeperShadow[d.fmx][d.fmy][2], d.x - 24 - 0.65f * d.z, d.y - 34 - 0.46f * d.z);
                        // batch.draw(Assets.keeperShadow[d.fmx][d.fmy][3], d.x - 24 + 0.65f * d.z, d.y - 34 - 0.46f * d.z);
                        // }
                    }
                }
            }
        }

        // players
        for (int i = 0; i < (scene.settings.time == MatchSettings.Time.NIGHT ? 4 : 1); i++) {
            for (int t = HOME; t <= AWAY; t++) {
                for (Player player : scene.team[t].lineup) {
                    if (player.role != Player.Role.GOALKEEPER) {
                        FrameData d = player.currentData;
                        if (d.isVisible) {
                            Integer[] origin = Assets.playerOrigins[d.fmy][d.fmx];
                            float mX = (i == 0 || i == 3) ? 0.65f : -0.65f;
                            float mY = (i == 0 || i == 1) ? 0.46f : -0.46f;
                            batch.draw(
                                Assets.playerShadow[d.fmx][d.fmy][i],
                                d.x - origin[0] + mX * d.z,
                                d.y - origin[1] + 5 + mY * d.z
                            );
                        }
                    }
                }
            }
        }

        batch.setColor(0xFFFFFF, 1f);
    }

    private void drawControlledPlayersNumbers() {
        for (int t = Match.HOME; t <= Match.AWAY; t++) {
            if (scene.team[t] != null) {
                int len = scene.team[t].lineup.size();
                for (int i = 0; i < len; i++) {
                    Player player = scene.team[t].lineup.get(i);
                    FrameData d = player.currentData;
                    if (d.isVisible) {
                        if (d.isHumanControlled) {
                            drawControlledPlayerMarker(player);
                            drawPlayerNumber(player);
                        } else if (Settings.showDevelopmentInfo && Settings.showPlayerNumber) {
                            Assets.font6.draw(
                                batch,
                                player.number,
                                Math.round(viewTransform.projectX(d.x, d.y)),
                                Math.round(viewTransform.projectY(d.x, d.y, d.z) - 40),
                                CENTER
                            );
                        }
                    }
                }
            }
        }
    }

    private void drawRosters() {

        int l = 13 + (guiWidth - 360) / 5 + 2;
        int r = guiWidth - l + 2;
        int w = r - l;
        int t = guiHeight / 2 - 270 + 2;
        int b = guiHeight / 2 + 270 + 2;
        int h = b - t;
        int m1 = t + h / 8 + 2;
        int m2 = t + h / 3 + 2;
        int hw = guiWidth / 2 + 2;

        // fading
        batch.end();
        gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        fadeRect(l + 2, t + 2, r - 2, b - 2, 0.35f, 0x000000);

        // line's shadows
        shapeRenderer.setColor(0x242424, guiAlpha);
        drawRosterLines(l, r, w, t, b, m1, m2, hw);

        l = l - 2;
        r = r - 2;
        t = t - 2;
        b = b - 2;
        m1 = m1 - 2;
        m2 = m2 - 2;
        hw = hw - 2;

        // lines
        shapeRenderer.setColor(0xFFFFFF, guiAlpha);
        drawRosterLines(l, r, w, t, b, m1, m2, hw);

        shapeRenderer.end();
        batch.begin();
        batch.setColor(0xFFFFFF, guiAlpha);

        // title
        int y = t + h / 23;
        Assets.font14.draw(batch, scene.competition.name, guiWidth / 2, y, Font.Align.CENTER);

        // city & stadium
        if (!scene.team[HOME].city.isEmpty() && !scene.team[HOME].stadium.isEmpty()) {
            y = t + h / 6;
            Assets.font14.draw(batch, scene.team[HOME].stadium + ", " + scene.team[HOME].city, guiWidth / 2, y, Font.Align.CENTER);
        }

        // club logos / national flags
        y = t + 13 * h / 100;

        if (scene.team[HOME].image != null) {
            int w0 = scene.team[HOME].image.getRegionWidth();
            int h0 = scene.team[HOME].image.getRegionHeight();
            float imageScale0 = (h0 > 70) ? 70f / h0 : 1f;
            int x0 = l + w / 23;
            int y0 = y - (int) (imageScale0 * h0) / 2;
            batch.setColor(0x242424, guiAlpha);
            batch.draw(scene.team[HOME].image, x0 + 2, y0 + 2, 0, 0, w0, h0, imageScale0, imageScale0, 0);
            batch.setColor(0xFFFFFF, guiAlpha);
            batch.draw(scene.team[HOME].image, x0, y0, 0, 0, w0, h0, imageScale0, imageScale0, 0);
        }
        if (scene.team[AWAY].image != null) {
            int w1 = scene.team[AWAY].image.getRegionWidth();
            int h1 = scene.team[AWAY].image.getRegionHeight();
            float imageScale1 = (h1 > 70) ? 70f / h1 : 1f;
            int x1 = r - w / 23 - (int) (imageScale1 * w1);
            int y1 = y - (int) (imageScale1 * h1) / 2;
            batch.setColor(0x242424, guiAlpha);
            batch.draw(scene.team[AWAY].image, x1 + 2, y1 + 2, 0, 0, w1, h1, imageScale1, imageScale1, 0);
            batch.setColor(0xFFFFFF, guiAlpha);
            batch.draw(scene.team[AWAY].image, x1, y1, 0, 0, w1, h1, imageScale1, imageScale1, 0);
        }

        // team name
        y = t + h / 4;
        Assets.font14.draw(batch, scene.team[HOME].name, l + w / 4, y, Font.Align.CENTER);
        Assets.font14.draw(batch, scene.team[AWAY].name, l + 3 * w / 4, y, Font.Align.CENTER);

        // players
        for (int tm = HOME; tm <= AWAY; tm++) {
            y = t + 16 * h / 42;
            for (int pos = 0; pos < Const.TEAM_SIZE; pos++) {
                Player player = scene.team[tm].lineupAtPosition(pos);
                Assets.font10.draw(batch, player.number, l + tm * w / 2 + w / 10, y, Font.Align.CENTER);
                Assets.font10.draw(batch, player.shirtName, l + tm * w / 2 + w / 7, y, Font.Align.LEFT);
                y = y + h / 23;
            }
        }

        // coach
        y = t + 7 * h / 8;
        Assets.font10.draw(batch, Assets.strings.get("COACH") + ":", l + 2 * w / 25, y, Font.Align.LEFT);
        Assets.font10.draw(batch, Assets.strings.get("COACH") + ":", l + 5 * w / 9, y, Font.Align.LEFT);

        y = t + 37 * h / 40;
        Assets.font10.draw(batch, scene.team[HOME].coach.name, l + w / 4, y, Font.Align.CENTER);
        Assets.font10.draw(batch, scene.team[AWAY].coach.name, l + 3 * w / 4, y, Font.Align.CENTER);
    }

    private void drawRosterLines(int l, int r, int w, int t, int b, int m1, int m2, int hw) {
        drawFrame(l, t, r - l, b - t);

        // middle
        shapeRenderer.rect(hw - 0.2f * w, m1, 0.4f * w, 1);
        shapeRenderer.rect(hw - 0.2f * w, m1 + 1, 0.4f * w, 1);

        // middle left
        shapeRenderer.rect(l + 0.05f * w, m2, hw - l - 0.1f * w, 1);
        shapeRenderer.rect(l + 0.05f * w, m2 + 1, hw - l - 0.1f * w, 1);

        // middle right
        shapeRenderer.rect(hw + 0.05f * w, m2, r - hw - 0.1f * w, 1);
        shapeRenderer.rect(hw + 0.05f * w, m2 + 1, r - hw - 0.1f * w, 1);
    }

    private void drawTime() {

        int minute = scene.getMinute();

        // "minutes"
        batch.draw(Assets.time[10], 46, 22);

        // units
        int digit = minute % 10;
        batch.draw(Assets.time[digit], 34, 22);

        // tens
        minute = (minute - digit) / 10;
        digit = minute % 10;
        if (minute > 0) {
            batch.draw(Assets.time[digit], 22, 22);
        }

        // hundreds
        minute = (minute - digit) / 10;
        digit = minute % 10;
        if (digit > 0) {
            batch.draw(Assets.time[digit], 10, 22);
        }
    }

    private void drawRadar() {

        final int RX = 10;
        final int RY = 60;
        final int RW = viewTransform.isHorizontal() ? 166 : 132;
        final int RH = viewTransform.isHorizontal() ? 132 : 166;

        batch.end();
        gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        fadeRect(RX, RY, RX + RW, RY + RH, 0.4f, 0x000000);

        shapeRenderer.setColor(0x000000, 1f);
        shapeRenderer.rect(RX, RY, 1, RH);
        shapeRenderer.rect(RX + 1, RY, RW - 2, 1);
        if (viewTransform.isHorizontal()) {
            shapeRenderer.rect(RX + RW / 2f, RY + 1, 1, RH - 2);
        } else {
            shapeRenderer.rect(RX + 1, RY + RH / 2f, RW - 2, 1);
        }
        shapeRenderer.rect(RX + 1, RY + RH - 1, RW - 2, 1);
        shapeRenderer.rect(RX + RW - 1, RY, 1, RH);

        // shirt colors
        int[] shirt1 = new int[2];
        int[] shirt2 = new int[2];
        for (int t = Match.HOME; t <= Match.AWAY; t++) {
            Kit kit = scene.team[t].getKit();
            shirt1[t] = kit.shirt1;
            shirt2[t] = kit.shirt2;
        }

        // placeholders
        for (Sprite sprite : allSprites) {
            if (sprite.getClass() == PlayerSprite.class) {
                Player player = ((PlayerSprite) sprite).player;
                FrameData d = player.currentData;
                if (d.isVisible && d.x > -TOUCH_LINE) {
                    int dx = RX + RW / 2 + (viewTransform.isHorizontal() ? d.y : d.x) / 8;
                    int dy = RY + RH / 2 + (viewTransform.isHorizontal() ? d.x : d.y) / 8;

                    shapeRenderer.setColor(0x242424, 1f);
                    shapeRenderer.rect(dx - 3, dy - 3, 6, 1);
                    shapeRenderer.rect(dx - 4, dy - 2, 1, 4);
                    shapeRenderer.rect(dx - 3, dy + 2, 6, 1);
                    shapeRenderer.rect(dx + 3, dy - 2, 1, 4);

                    shapeRenderer.setColor(shirt1[player.team.index], 1f);
                    shapeRenderer.rect(dx - 3, dy - 2, 3, 4);

                    shapeRenderer.setColor(shirt2[player.team.index], 1f);
                    shapeRenderer.rect(dx, dy - 2, 3, 4);
                }
            }
        }

        shapeRenderer.end();
        batch.begin();
        batch.setColor(0xFFFFFF, guiAlpha);

        // controlled players numbers
        if (scene.displayControlledPlayer) {
            for (Sprite sprite : allSprites) {
                if (sprite.getClass() == PlayerSprite.class) {
                    Player player = ((PlayerSprite) sprite).player;
                    FrameData d = player.currentData;
                    if (d.isVisible && (d.x > -TOUCH_LINE) && (player.inputDevice != player.ai)) {
                        int dx = RX + RW / 2 + (viewTransform.isHorizontal() ? d.y : d.x) / 8 + 1;
                        int dy = RY + RH / 2 + (viewTransform.isHorizontal() ? d.x : d.y) / 8 - 10;

                        int f0 = player.number % 10;
                        int f1 = (player.number - f0) / 10 % 10;

                        int w0, w1;
                        if (f1 > 0) {
                            w0 = 4 - (f0 == 1 ? 2 : 0);
                            w1 = 4 - (f1 == 1 ? 2 : 0);
                            dx = dx - (w0 + w1) / 2;
                            batch.draw(Assets.tinyNumbers[f1], dx, dy);
                            dx = dx + w1;
                            batch.draw(Assets.tinyNumbers[f0], dx, dy);
                        } else {
                            w0 = 4 - (f0 == 1 ? 2 : 0);
                            dx = dx - w0 / 2;
                            batch.draw(Assets.tinyNumbers[f0], dx, dy);
                        }
                    }
                }
            }
        }
    }

    private void drawScore() {
        // default values
        int h0 = 0;
        int w0 = 0;
        int w1 = 0;
        int h1 = 0;
        float imageScale0 = 1f;
        float imageScale1 = 1f;

        // max rows of rows
        int rows = Math.max(scene.scorers.rows[HOME].size(), scene.scorers.rows[AWAY].size());

        // size of club logos / national flags
        if (scene.team[HOME].image != null) {
            w0 = scene.team[HOME].image.getRegionWidth();
            h0 = scene.team[HOME].image.getRegionHeight();
            if (h0 > 70) {
                imageScale0 = 70f / h0;
            }
        }
        if (scene.team[AWAY].image != null) {
            w1 = scene.team[AWAY].image.getRegionWidth();
            h1 = scene.team[AWAY].image.getRegionHeight();
            if (h1 > 70) {
                imageScale1 = 70f / h1;
            }
        }

        int hMax = Math.max((int) (imageScale0 * h0), (int) (imageScale1 * h1));
        int y0 = guiHeight - 16 - Math.max(hMax, 14 * rows);

        // club logos / national flags
        if (scene.team[HOME].image != null) {
            int x = 12;
            int y = y0 + 8 + (hMax - (int) (imageScale0 * h0)) / 2;
            batch.setColor(0x242424, guiAlpha);
            batch.draw(scene.team[HOME].image, x + 2, y + 2, 0, 0, w0, h0, imageScale0, imageScale0, 0);
            batch.setColor(0xFFFFFF, guiAlpha);
            batch.draw(scene.team[HOME].image, x, y, 0, 0, w0, h0, imageScale0, imageScale0, 0);
        }
        if (scene.team[AWAY].image != null) {
            int x = guiWidth - (int) (imageScale1 * w1) - 12;
            int y = y0 + 8 + (hMax - (int) (imageScale1 * h1)) / 2;
            batch.setColor(0x242424, guiAlpha);
            batch.draw(scene.team[AWAY].image, x + 2, y + 2, 0, 0, w1, h1, imageScale1, imageScale1, 0);
            batch.setColor(0xFFFFFF, guiAlpha);
            batch.draw(scene.team[AWAY].image, x, y, 0, 0, w1, h1, imageScale1, imageScale1, 0);
        }

        // teams
        Assets.font14.draw(batch, scene.team[HOME].name, +12, y0 - 22, Font.Align.LEFT);
        Assets.font14.draw(batch, scene.team[AWAY].name, guiWidth - 10, y0 - 22, Font.Align.RIGHT);

        // bars
        batch.end();
        gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0xFFFFFF, guiAlpha);

        shapeRenderer.rect(10, y0, guiWidth / 2f - 22, 2);
        shapeRenderer.rect(guiWidth / 2f + 12, y0, guiWidth / 2f - 22, 2);

        shapeRenderer.setColor(0x242424, guiAlpha);
        shapeRenderer.rect(12, y0 + 2, guiWidth / 2f - 22, 2);
        shapeRenderer.rect(guiWidth / 2f + 14, y0 + 2, guiWidth / 2f - 22, 2);

        shapeRenderer.end();
        batch.begin();
        batch.setColor(0xFFFFFF, guiAlpha);

        // home score
        int f0 = scene.stats[Match.HOME].goals % 10;
        int f1 = ((scene.stats[Match.HOME].goals - f0) / 10) % 10;

        if (f1 > 0) {
            batch.draw(Assets.score[f1], guiWidth / 2f - 15 - 48, y0 - 40);
        }
        batch.draw(Assets.score[f0], guiWidth / 2f - 15 - 24, y0 - 40);

        // "-"
        batch.draw(Assets.score[10], guiWidth / 2f - 9, y0 - 40);

        // away score
        f0 = scene.stats[Match.AWAY].goals % 10;
        f1 = (scene.stats[Match.AWAY].goals - f0) / 10 % 10;

        if (f1 > 0) {
            batch.draw(Assets.score[f1], guiWidth / 2f + 17, y0 - 40);
            batch.draw(Assets.score[f0], guiWidth / 2f + 17 + 24, y0 - 40);
        } else {
            batch.draw(Assets.score[f0], guiWidth / 2f + 17, y0 - 40);
        }

        // scorers
        for (int t = HOME; t <= AWAY; t++) {
            int y = y0 + 4;
            for (String row : scene.scorers.rows[t]) {
                int x = guiWidth / 2 + (t == HOME ? -12 : +14);
                Font.Align align = t == HOME ? Font.Align.RIGHT : Font.Align.LEFT;
                Assets.font10.draw(batch, row, x, y, align);
                y += 14;
            }
        }
    }

    private void drawPenaltiesScore() {
        // default values
        int h0 = 0;
        int w0 = 0;
        int w1 = 0;
        int h1 = 0;
        float imageScale0 = 1f;
        float imageScale1 = 1f;

        // max rows of rows
        int rows = Math.max(scene.penalties[HOME].size(), scene.penalties[AWAY].size());

        // size of club logos / national flags
        if (scene.team[HOME].image != null) {
            w0 = scene.team[HOME].image.getRegionWidth();
            h0 = scene.team[HOME].image.getRegionHeight();
            if (h0 > 70) {
                imageScale0 = 70f / h0;
            }
        }
        if (scene.team[AWAY].image != null) {
            w1 = scene.team[AWAY].image.getRegionWidth();
            h1 = scene.team[AWAY].image.getRegionHeight();
            if (h1 > 70) {
                imageScale1 = 70f / h1;
            }
        }

        int hMax = Math.max((int) (imageScale0 * h0), (int) (imageScale1 * h1));
        int y0 = guiHeight - 16 - Math.max(hMax, 14 * rows);

        // club logos / national flags
        if (scene.team[HOME].image != null) {
            int x = 12;
            int y = y0 + 8 + (hMax - (int) (imageScale0 * h0)) / 2;
            batch.setColor(0x242424, guiAlpha);
            batch.draw(scene.team[HOME].image, x + 2, y + 2, 0, 0, w0, h0, imageScale0, imageScale0, 0);
            batch.setColor(0xFFFFFF, guiAlpha);
            batch.draw(scene.team[HOME].image, x, y, 0, 0, w0, h0, imageScale0, imageScale0, 0);
        }
        if (scene.team[AWAY].image != null) {
            int x = guiWidth - (int) (imageScale1 * w1) - 12;
            int y = y0 + 8 + (hMax - (int) (imageScale1 * h1)) / 2;
            batch.setColor(0x242424, guiAlpha);
            batch.draw(scene.team[AWAY].image, x + 2, y + 2, 0, 0, w1, h1, imageScale1, imageScale1, 0);
            batch.setColor(0xFFFFFF, guiAlpha);
            batch.draw(scene.team[AWAY].image, x, y, 0, 0, w1, h1, imageScale1, imageScale1, 0);
        }

        // teams
        Assets.font14.draw(batch, scene.team[HOME].name, +12, y0 - 22, Font.Align.LEFT);
        Assets.font14.draw(batch, scene.team[AWAY].name, guiWidth - 10, y0 - 22, Font.Align.RIGHT);

        // bars
        batch.end();
        gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0xFFFFFF, guiAlpha);

        shapeRenderer.rect(10, y0, guiWidth / 2f - 22, 2);
        shapeRenderer.rect(guiWidth / 2f + 12, y0, guiWidth / 2f - 22, 2);

        shapeRenderer.setColor(0x242424, guiAlpha);
        shapeRenderer.rect(12, y0 + 2, guiWidth / 2f - 22, 2);
        shapeRenderer.rect(guiWidth / 2f + 14, y0 + 2, guiWidth / 2f - 22, 2);

        shapeRenderer.end();
        batch.begin();
        batch.setColor(0xFFFFFF, guiAlpha);

        // home score
        int homeScore = scene.penaltyGoals(HOME);
        int f0 = homeScore % 10;
        int f1 = ((homeScore - f0) / 10) % 10;

        if (f1 > 0) {
            batch.draw(Assets.score[f1], guiWidth / 2f - 15 - 48, y0 - 40);
        }
        batch.draw(Assets.score[f0], guiWidth / 2f - 15 - 24, y0 - 40);

        // "-"
        batch.draw(Assets.score[10], guiWidth / 2f - 9, y0 - 40);

        // away score
        int awayScore = scene.penaltyGoals(AWAY);
        f0 = awayScore % 10;
        f1 = (awayScore - f0) / 10 % 10;

        if (f1 > 0) {
            batch.draw(Assets.score[f1], guiWidth / 2f + 17, y0 - 40);
            batch.draw(Assets.score[f0], guiWidth / 2f + 17 + 24, y0 - 40);
        } else {
            batch.draw(Assets.score[f0], guiWidth / 2f + 17, y0 - 40);
        }

        // scorers
        for (int t = HOME; t <= AWAY; t++) {
            int y = y0 + 4;
            for (Match.Penalty penalty : scene.penalties[t]) {
                int x = guiWidth / 2 + (t == HOME ? -12 : +14);
                String text = "";
                switch (penalty.state) {
                    case TO_KICK:
                        text = " ";
                        break;
                    case SCORED:
                        text = t == HOME ? penalty.kicker.shirtName + " " + (char) 25 : (char) 25 + " " + penalty.kicker.shirtName;
                        break;
                    case MISSED:
                        text = t == HOME ? penalty.kicker.shirtName + " " + (char) 26 : (char) 26 + " " + penalty.kicker.shirtName;
                        break;
                }

                Font.Align align = t == HOME ? Font.Align.RIGHT : Font.Align.LEFT;
                Assets.font10.draw(batch, text, x, y, align);
                y += 14;
            }
        }
    }

    private void drawStatistics() {

        int l = 13 + (guiWidth - 360) / 5 + 2;
        int r = guiWidth - l + 2;
        int w = r - l;
        int t = guiHeight / 2 - 270 + 2;
        int b = guiHeight / 2 + 270 + 2;
        int h = b - t;
        int hw = guiWidth / 2;

        // fading
        batch.end();
        gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // top strip
        fadeRect(l + 2, t + 2, r - 2, t + h / 10 + 1, 0.35f, 0x000000);

        // middle strips
        int i = t + h / 10 + 2;
        for (int j = 1; j < 9; j++) {
            fadeRect(l + 2, i + 1, r - 2, i + h / 10 - 1, 0.35f, 0x000000);
            i = i + h / 10;
        }

        // bottom strip
        fadeRect(l + 2, i + 1, r - 2, b - 2, 0.35f, 0x000000);

        // frame shadow
        shapeRenderer.setColor(0x242424, guiAlpha);
        drawFrame(l, t, r - l, b - t);

        l = l - 2;
        r = r - 2;
        t = t - 2;
        b = b - 2;

        // frame
        shapeRenderer.setColor(0xFFFFFF, guiAlpha);
        drawFrame(l, t, r - l, b - t);

        shapeRenderer.end();
        batch.begin();
        batch.setColor(0xFFFFFF, guiAlpha);

        MatchStats homeStats = scene.stats[Match.HOME];
        MatchStats awayStats = scene.stats[Match.AWAY];

        int possHome = Math.round(100 * (1f + scene.stats[Match.HOME].ballPossession) / (2f + homeStats.ballPossession + awayStats.ballPossession));
        int possAway = 100 - possHome;

        // text
        int lc = l + w / 5;
        int rc = r - w / 5;
        i = t + h / 20 - 8;
        Assets.font14.draw(batch, Assets.strings.get("MATCH STATISTICS"), hw, i, Font.Align.CENTER);

        i = i + h / 10;
        Assets.font14.draw(batch, scene.team[Match.HOME].name, lc, i, Font.Align.CENTER);
        Assets.font14.draw(batch, scene.team[Match.AWAY].name, rc, i, Font.Align.CENTER);

        i = i + h / 10;
        int homeGoals = scene.penalties[HOME].isEmpty() ? homeStats.goals : scene.penaltiesScore(HOME);
        int awayGoals = scene.penalties[AWAY].isEmpty() ? awayStats.goals : scene.penaltiesScore(AWAY);
        Assets.font14.draw(batch, homeGoals, lc, i, Font.Align.CENTER);
        Assets.font14.draw(batch, Assets.strings.get("MATCH STATISTICS.GOALS"), hw, i, Font.Align.CENTER);
        Assets.font14.draw(batch, awayGoals, rc, i, Font.Align.CENTER);

        i = i + h / 10;
        Assets.font14.draw(batch, possHome, lc, i, Font.Align.CENTER);
        Assets.font14.draw(batch, Assets.strings.get("MATCH STATISTICS.POSSESSION"), hw, i, Font.Align.CENTER);
        Assets.font14.draw(batch, possAway, rc, i, Font.Align.CENTER);

        i = i + h / 10;
        Assets.font14.draw(batch, homeStats.overallShots, lc, i, Font.Align.CENTER);
        Assets.font14.draw(batch, Assets.strings.get("MATCH STATISTICS.GOAL ATTEMPTS"), hw, i, Font.Align.CENTER);
        Assets.font14.draw(batch, awayStats.overallShots, rc, i, Font.Align.CENTER);

        i = i + h / 10;
        Assets.font14.draw(batch, homeStats.centeredShots, lc, i, Font.Align.CENTER);
        Assets.font14.draw(batch, Assets.strings.get("MATCH STATISTICS.ON TARGET"), hw, i, Font.Align.CENTER);
        Assets.font14.draw(batch, awayStats.centeredShots, rc, i, Font.Align.CENTER);

        i = i + h / 10;
        Assets.font14.draw(batch, homeStats.cornersWon, lc, i, Font.Align.CENTER);
        Assets.font14.draw(batch, Assets.strings.get("MATCH STATISTICS.CORNERS WON"), hw, i, Font.Align.CENTER);
        Assets.font14.draw(batch, awayStats.cornersWon, rc, i, Font.Align.CENTER);

        i = i + h / 10;
        Assets.font14.draw(batch, homeStats.foulsConceded, lc, i, Font.Align.CENTER);
        Assets.font14.draw(batch, Assets.strings.get("MATCH STATISTICS.FOULS CONCEDED"), hw, i, Font.Align.CENTER);
        Assets.font14.draw(batch, awayStats.foulsConceded, rc, i, Font.Align.CENTER);

        i = i + h / 10;
        Assets.font14.draw(batch, homeStats.yellowCards, lc, i, Font.Align.CENTER);
        Assets.font14.draw(batch, Assets.strings.get("MATCH STATISTICS.BOOKINGS"), hw, i, Font.Align.CENTER);
        Assets.font14.draw(batch, awayStats.yellowCards, rc, i, Font.Align.CENTER);

        i = i + h / 10;
        Assets.font14.draw(batch, homeStats.redCards, lc, i, Font.Align.CENTER);
        Assets.font14.draw(batch, Assets.strings.get("MATCH STATISTICS.SENDINGS OFF"), hw, i, Font.Align.CENTER);
        Assets.font14.draw(batch, awayStats.redCards, rc, i, Font.Align.CENTER);

        batch.setColor(0xFFFFFF, 1f);
    }

    private void drawBenchPlayers() {
        int w = 270;
        int h = 18;

        int x = guiWidth / 3 + 2;
        int y = guiHeight / 2 - 100 + 2;

        // objects' shadows //
        batch.end();
        gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0x242424, guiAlpha);

        // title
        drawFrame(x, y, w, h + 2);

        // image
        shapeRenderer.rect(x + w / 2f - 41, y + 41, 82, 66);

        // list
        drawFrame(x, y + 125, w, scene.getSettings().benchSize * h + 6);

        // slots
        int color = 0x242424;

        if (scene.getFsm().benchStatus.selectedPosition == -1) {
            color = GLColor.sweepColor(color, 0xFFDD33);
        }

        fadeRect(x, y + 2, x + w - 2, y + h, 0.6f, color);

        for (int pos = 0; pos < scene.getSettings().benchSize; pos++) {
            color = 0x242424;

            if (scene.getFsm().benchStatus.selectedPosition == pos) {
                color = GLColor.sweepColor(color, 0xFFDD33);
            }
            fadeRect(x, y + 125 + 4 + pos * h, x + w - 2, y + 125 + 2 + (pos + 1) * h, 0.6f, color);
        }

        x = x - 2;
        y = y - 2;

        // objects //
        shapeRenderer.setColor(0xFFFFFF, guiAlpha);

        // title
        drawFrame(x, y, w, h + 2);

        // list
        drawFrame(x, y + 125, w, scene.getSettings().benchSize * h + 6);

        shapeRenderer.end();
        batch.begin();
        batch.setColor(0xFFFFFF, guiAlpha);

        // image
        batch.draw(Assets.bench[0], x + w / 2f - 41, y + 41);

        Assets.font10.draw(batch, Assets.strings.get("BENCH"), x + w / 2, y + 3, Font.Align.CENTER);

        int benchSize = min(scene.getSettings().benchSize, scene.getFsm().benchStatus.team.lineup.size() - TEAM_SIZE);
        for (int pos = 0; pos < benchSize; pos++) {
            Player player = scene.getFsm().benchStatus.team.lineupAtPosition(TEAM_SIZE + pos);

            if (!player.getState().checkId(STATE_SUBSTITUTED)) {
                Assets.font10.draw(batch, player.number, x + 25, y + 5 + 125 + pos * h, Font.Align.CENTER);
                Assets.font10.draw(batch, player.shirtName, x + 45, y + 5 + 125 + pos * h, Font.Align.LEFT);
                Assets.font10.draw(batch, Assets.strings.get(player.getRoleLabel()), x + w - 20, y + 5 + 125 + pos * h, Font.Align.CENTER);
            }
        }
    }

    private void drawBenchFormation() {
        int w = 270;
        int h = 18;

        int x = guiWidth / 3 + 2;
        int y = guiHeight / 2 - 150 + 2;

        // objects' shadows //
        batch.end();
        gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0x242424, guiAlpha);

        // title
        drawFrame(x, y, w, h + 2);

        // image
        shapeRenderer.rect(x + w / 2f - 41, y + 41, 82, 66);

        // list
        drawFrame(x, y + 125, w, TEAM_SIZE * h + 6);

        // slots
        int color = 0x242424;
        if (scene.getFsm().benchStatus.selectedPosition == -1) {
            // substitution - yellow
            if (scene.getFsm().benchStatus.substPosition != -1) {
                color = GLColor.sweepColor(color, 0xFFFF33);
            }
            // swap - blue
            else {
                color = GLColor.sweepColor(color, 0x33DDFF);
            }
        }
        fadeRect(x, y + 2, x + w - 2, y + h, 0.6f, color);

        for (int pos = 0; pos < TEAM_SIZE; pos++) {
            color = 0x242424;
            if (pos == scene.getFsm().benchStatus.swapPosition) {
                color = 0x33DDFF;
            }

            if (pos == scene.getFsm().benchStatus.selectedPosition) {
                // substitution - yellow
                if (scene.getFsm().benchStatus.substPosition != -1) {
                    color = GLColor.sweepColor(0x242424, 0xFFFF33);
                }
                // swap - blue
                else {
                    color = GLColor.sweepColor(color, 0x33DDFF);
                }
            }
            fadeRect(x, y + 125 + 4 + pos * h, x + w - 2, y + 125 + 2 + (pos + 1) * h, 0.6f, color);
        }

        x = x - 2;
        y = y - 2;

        // objects //
        shapeRenderer.setColor(0xFFFFFF, guiAlpha);

        // title
        drawFrame(x, y, w, h + 2);

        // list
        drawFrame(x, y + 125, w, TEAM_SIZE * h + 6);

        shapeRenderer.end();
        batch.begin();
        batch.setColor(0xFFFFFF, guiAlpha);

        // image
        batch.draw(Assets.bench[1], x + w / 2f - 41, y + 41);

        Assets.font10.draw(batch, Assets.strings.get("FORMATION"), x + w / 2, y + 3, Font.Align.CENTER);

        for (int pos = 0; pos < TEAM_SIZE; pos++) {

            Player ply = scene.getFsm().benchStatus.team.lineupAtPosition(pos);

            if (!ply.checkState(STATE_SENT_OFF)) {
                Assets.font10.draw(batch, ply.number, x + 25, y + 5 + 125 + pos * h, Font.Align.CENTER);
                Assets.font10.draw(batch, ply.shirtName, x + 45, y + 5 + 125 + pos * h, Font.Align.LEFT);
                if (scene.referee.hasYellowCard(ply)) {
                    Assets.font10.draw(batch, "" + (char) 14, x + w - 45, y + 5 + 125 + pos * h, Font.Align.CENTER);
                }
                Assets.font10.draw(batch, Assets.strings.get(ply.getRoleLabel()), x + w - 20, y + 5 + 125 + pos * h, Font.Align.CENTER);
            }
        }
    }

    private void drawTacticsSwitch() {
        int w = 180;
        int h = 18;

        int x = guiWidth / 3 + 45 + 2;
        int y = guiHeight / 2 - 186 + 2;

        // objects' shadows //
        batch.end();
        gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0x242424, guiAlpha);

        // image
        shapeRenderer.rect(x + w / 2f - 41, y, 82, 66);

        // frame
        drawFrame(x, y + 80, w, 18 * h + 6);

        // slots
        for (int i = 0; i < 18; i++) {
            int color = 0x242424;
            if (i == scene.getFsm().benchStatus.selectedTactics) {
                color = GLColor.sweepColor(color, 0xFFAA33);
            }
            fadeRect(x, y + 84 + h * i, x + w - 2, y + 82 + h * (i + 1), 0.6f, color);
        }

        x = x - 2;
        y = y - 2;

        // objects //
        shapeRenderer.setColor(0xFFFFFF, guiAlpha);

        drawFrame(x, y + 80, w, 18 * h + 6);

        shapeRenderer.end();
        batch.begin();
        batch.setColor(0xFFFFFF, guiAlpha);

        batch.draw(Assets.bench[1], x + w / 2f - 41, y);

        for (int i = 0; i < 18; i++) {
            Assets.font10.draw(batch, Tactics.codes[i], x + w / 2, y + 85 + h * i, Font.Align.CENTER);
        }
    }

    private void drawReplayGui() {
        int f = Math.round(1f * scene.subframe / GLGame.SUBFRAMES) % 32;
        if (f < 16) {
            Assets.font10.draw(batch, gettext("ACTION REPLAY"), 30, 22, Font.Align.LEFT);
        }
        if (Settings.showDevelopmentInfo) {
            Assets.font10.draw(batch, "FRAME: " + (scene.subframe / 8) + " / " + Const.REPLAY_FRAMES, 30, 42, Font.Align.LEFT);
            Assets.font10.draw(batch, "SUBFRAME: " + scene.subframe + " / " + Const.REPLAY_SUBFRAMES, 30, 62, Font.Align.LEFT);
        }

        float a = matchState.replayPosition * 360f / Const.REPLAY_SUBFRAMES;

        batch.end();
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.setAutoShapeType(true);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0x242424, guiAlpha);
        shapeRenderer.arc(20, 32, 6, 270 + a, 360 - a);
        shapeRenderer.setColor(0xFF0000, guiAlpha);
        shapeRenderer.arc(18, 30, 6, 270 + a, 360 - a);
        shapeRenderer.end();
        batch.begin();
    }

    private void drawHighlightsGui() {
        int f = Math.round(1f * scene.subframe / GLGame.SUBFRAMES) % 32;
        if (
            //showCurrentRecord &&
            f < 16) {
            Assets.font10.draw(batch, (scene.recorder.getCurrent() + 1) + "/" + scene.recorder.getRecorded(), 30, 22, Font.Align.LEFT);
        }
        if (Settings.showDevelopmentInfo) {
            Assets.font10.draw(batch, "FRAME: " + (scene.subframe / 8) + " / " + Const.REPLAY_FRAMES, 30, 42, Font.Align.LEFT);
            Assets.font10.draw(batch, "SUBFRAME: " + scene.subframe + " / " + Const.REPLAY_SUBFRAMES, 30, 62, Font.Align.LEFT);
        }

        float a = matchState.replayPosition * 360f / Const.REPLAY_SUBFRAMES;

        batch.end();
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.setAutoShapeType(true);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0x242424, guiAlpha);
        shapeRenderer.arc(20, 32, 6, 270 + a, 360 - a);
        shapeRenderer.setColor(0xFF0000, guiAlpha);
        shapeRenderer.arc(18, 30, 6, 270 + a, 360 - a);
        shapeRenderer.end();
        batch.begin();
    }

    void drawYellowCard(Player player) {
        FrameData d = player.currentData;
        if ((scene.stateTimer % (SECOND / 2)) > SECOND / 4) {
            Assets.font6.draw(
                batch,
                "" + (char) 14,
                Math.round(viewTransform.projectX(d.x, d.y) + 1),
                Math.round(viewTransform.projectY(d.x, d.y, d.z) - 40),
                CENTER
            );
        }
    }

    void drawRedCard(Player player) {
        FrameData d = player.currentData;
        if ((scene.stateTimer % (SECOND / 2)) > SECOND / 4) {
            Assets.font6.draw(
                batch,
                "" + (char) 15,
                Math.round(viewTransform.projectX(d.x, d.y) + 1),
                Math.round(viewTransform.projectY(d.x, d.y, d.z) - 40),
                CENTER
            );
        }
    }
}
