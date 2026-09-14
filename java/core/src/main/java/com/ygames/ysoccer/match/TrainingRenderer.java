package com.ygames.ysoccer.match;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.ygames.ysoccer.framework.Assets;
import com.ygames.ysoccer.framework.Font;
import com.ygames.ysoccer.framework.GLGame;
import com.ygames.ysoccer.framework.GLGraphics;
import com.ygames.ysoccer.framework.Settings;

import static com.badlogic.gdx.Gdx.gl;
import static com.ygames.ysoccer.framework.Assets.gettext;
import static com.ygames.ysoccer.match.Match.AWAY;
import static com.ygames.ysoccer.match.Match.HOME;

public class TrainingRenderer extends SceneRenderer<Training> {

    private TrainingState trainingState;
    private final BallSprite ballSprite;

    public TrainingRenderer(GLGraphics glGraphics, Training training) {
        super(glGraphics, training);
        this.ball = training.ball;

        hotKeys = new TrainingHotKeys(training, this);

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        scene.camera.x = 0.5f * (Const.PITCH_W - scene.camera.getWorldViewportWidth());
        scene.camera.y = 0.5f * (Const.PITCH_H - scene.camera.getWorldViewportHeight());
        for (int i = 0; i < Const.REPLAY_SUBFRAMES; i++) {
            scene.vCameraX[i] = Math.round(scene.camera.x);
            scene.vCameraY[i] = Math.round(scene.camera.y);
        }

        ballSprite = new BallSprite(glGraphics, training.ball, viewTransform);
        allSprites.add(ballSprite);
        CoachSprite coachSprite = new CoachSprite(glGraphics, training.team[HOME].coach, viewTransform);
        allSprites.add(coachSprite);

        for (int t = HOME; t <= AWAY; t++) {
            int len = training.team[t].lineup.size();
            for (int i = 0; i < len; i++) {
                PlayerSprite playerSprite = new PlayerSprite(glGraphics, training.team[t].lineup.get(i), viewTransform);
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
    }

    public void render() {
        trainingState = scene.getState();

        glGraphics.light = scene.light;

        gl.glEnable(GL20.GL_BLEND);
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        configureWorldCamera();
        batch.begin();

        beginGroundTransform();
        renderBackground();
        endGroundTransform();

        if (Settings.showDevelopmentInfo && Settings.showBallPredictions) {
            drawBallPredictions(ball);
        }

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

        if (trainingState.displayControlledPlayer) {
            drawControlledPlayersNumbers();
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
        if (scene.ball.owner != null) {
            drawPlayerNumberAndName(scene.ball.owner);
        }

        // wind vane
        if (scene.settings.wind.speed > 0) {
            batch.draw(Assets.wind[scene.settings.wind.direction][scene.settings.wind.speed - 1], guiWidth - 50, 20);
        }

        // messages
        if (hotKeys.messageTimer > 0) {
            batch.setColor(0xFFFFFF, guiAlpha);
            Assets.font10.draw(batch, hotKeys.message, guiWidth / 2, 1, Font.Align.CENTER);
        }

        if (trainingState.displayPause) {
            Assets.font10.draw(batch, gettext("PAUSE"), guiWidth / 2, 22, Font.Align.CENTER);
        }

        if (trainingState.displayReplayGui) {
            int f = Math.round(1f * scene.subframe / GLGame.SUBFRAMES) % 32;
            if (f < 16) {
                Assets.font10.draw(batch, gettext("ACTION REPLAY"), 30, 22, Font.Align.LEFT);
            }
            if (Settings.showDevelopmentInfo) {
                Assets.font10.draw(batch, "FRAME: " + (scene.subframe / 8) + " / " + Const.REPLAY_FRAMES, 30, 42, Font.Align.LEFT);
                Assets.font10.draw(batch, "SUBFRAME: " + scene.subframe + " / " + Const.REPLAY_SUBFRAMES, 30, 62, Font.Align.LEFT);
            }

            float a = trainingState.replayPosition * 360f / Const.REPLAY_SUBFRAMES;

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

        if (trainingState.displayReplayControls) {
            int frameX = 1 + trainingState.inputDevice.x1;
            int frameY = 1 + trainingState.inputDevice.y1;
            batch.draw(Assets.replaySpeed[frameX][frameY], guiWidth - 50, guiHeight - 50);
        }

        batch.end();
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
                        if (scene.settings.time == MatchSettings.Time.NIGHT) {
                            // TODO activate after getting keeper shadows
                            // batch.draw(Assets.keeperShadow[d.fmx][d.fmy][1], d.x - 24 - 0.65f * d.z, d.y - 34 + 0.46f * d.z);
                            // batch.draw(Assets.keeperShadow[d.fmx][d.fmy][2], d.x - 24 - 0.65f * d.z, d.y - 34 - 0.46f * d.z);
                            // batch.draw(Assets.keeperShadow[d.fmx][d.fmy][3], d.x - 24 + 0.65f * d.z, d.y - 34 - 0.46f * d.z);
                        }
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
                    if (player.inputDevice != player.ai && player.isVisible) {
                        drawControlledPlayerMarker(player);
                        drawPlayerNumber(player);
                    } else if (Settings.development && Settings.showPlayerNumber) {
                        drawPlayerNumber(player);
                    }
                }
            }
        }
    }
}
