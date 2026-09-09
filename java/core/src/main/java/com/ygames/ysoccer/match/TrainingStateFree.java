package com.ygames.ysoccer.match;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.ygames.ysoccer.framework.EMath;
import com.ygames.ysoccer.framework.GLGame;
import com.ygames.ysoccer.framework.InputDevice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.badlogic.gdx.Input.Keys.ESCAPE;
import static com.ygames.ysoccer.match.Const.BALL_R;
import static com.ygames.ysoccer.match.Const.BENCH_X;
import static com.ygames.ysoccer.match.Const.BENCH_Y_UP;
import static com.ygames.ysoccer.match.Const.GOAL_LINE;
import static com.ygames.ysoccer.match.Const.TOUCH_LINE;
import static com.ygames.ysoccer.match.Match.AWAY;
import static com.ygames.ysoccer.match.Match.HOME;
import static com.ygames.ysoccer.match.Player.Role.GOALKEEPER;
import static com.ygames.ysoccer.match.PlayerFsm.Id.STATE_KEEPER_KICK_ANGLE;
import static com.ygames.ysoccer.match.PlayerFsm.Id.STATE_KEEPER_POSITIONING;
import static com.ygames.ysoccer.match.PlayerFsm.Id.STATE_REACH_TARGET;
import static com.ygames.ysoccer.match.PlayerFsm.Id.STATE_STAND_RUN;
import static com.ygames.ysoccer.match.SceneFsm.ActionType.HOLD_FOREGROUND;
import static com.ygames.ysoccer.match.TrainingFsm.StateId.FREE;
import static com.ygames.ysoccer.match.TrainingFsm.StateId.REPLAY;

class TrainingStateFree extends TrainingState {

    private Player lastTrained;
    private final Player[] keepers;
    private final ManualPlayerSwitcher manualPlayerSwitcher;

    TrainingStateFree(TrainingFsm fsm) {
        super(FREE, fsm);

        displayControlledPlayer = true;

        keepers = new Player[]{null, null};
        manualPlayerSwitcher = new ManualPlayerSwitcher();
    }

    @Override
    void entryActions() {
        super.entryActions();

        setIntroPositions();
        scene.findNearest();
        scene.resetData();

        lastTrained = team[HOME].lineup.get(0);
        manualPlayerSwitcher.reset();
    }

    @Override
    void exitActions() {
        super.exitActions();
        manualPlayerSwitcher.reset();
    }

    @Override
    void doActions(float deltaTime) {
        super.doActions(deltaTime);

        float timeLeft = deltaTime;
        while (timeLeft >= GLGame.SUBFRAME_DURATION) {

            manualPlayerSwitcher.update();

            if (scene.subframe % GLGame.SUBFRAMES == 0) {
                scene.updateFrameDistance();
            }

            scene.updatePlayers(true);
            scene.updateStates();
            scene.findNearest();
            scene.updateBall();

            ball.collisionFlagPosts();
            ball.collisionGoal();
            ball.collisionJumpers();
            ball.collisionNet();
            ball.collisionNetOut();

            if (ball.v == 0 && (Math.abs(ball.y) >= (GOAL_LINE + BALL_R) || (Math.abs(ball.x) > TOUCH_LINE + BALL_R))) {
                resetBall();
            }

            for (int t = Match.HOME; t <= AWAY; t++) {
                for (Player player : team[t].lineup) {
                    if (player.inputDevice == player.ai) {
                        if (player.checkState(STATE_STAND_RUN)) {
                            player.watchPosition(ball.x, ball.y);
                        }
                        if (player.checkState(STATE_KEEPER_KICK_ANGLE)
                            && !Const.isInsideGoalArea(lastTrained.x, lastTrained.y, player.side)) {
                            if (player.getState().timer > Const.SECOND) {
                                player.setState(STATE_KEEPER_POSITIONING);
                                ball.a = player.angleToPoint(lastTrained.x, lastTrained.y);
                                ball.v = 180;
                            }
                        }
                    }
                }
            }

            if ((scene.subframe % GLGame.VIRTUAL_REFRESH_RATE) == 0) {
                scene.ball.updatePrediction();
            }

            scene.nextSubframe();

            scene.save();

            scene.camera.update();

            timeLeft -= GLGame.SUBFRAME_DURATION;
        }

        if ((ball.owner != null)
            && (ball.owner != lastTrained)
            && ball.owner.role == GOALKEEPER) {

            // swap goalkeepers
            if (ball.owner != keepers[ball.owner.team.index]) {
                Team team = ball.owner.team;
                Player newKeeper = ball.owner;
                Player oldKeeper = keepers[team.index];

                newKeeper.setState(STATE_KEEPER_POSITIONING);

                resetTargetPosition(oldKeeper);
                oldKeeper.setState(STATE_REACH_TARGET);

                Collections.swap(team.lineup, oldKeeper.lineupIndex(), newKeeper.lineupIndex());
                keepers[team.index] = newKeeper;

                ball.a = newKeeper.angleToPoint(lastTrained.x, lastTrained.y);
                ball.v = 180;
            }
        }

        if (ball.owner != null
            && ball.owner != keepers[HOME]
            && ball.owner != keepers[AWAY]
            && ball.owner.inputDevice != ball.owner.ai) {
            lastTrained = ball.owner;
        }

        InputDevice humanInput = lastTrained.inputDevice;
        List<Player> switchablePlayers = switchablePlayers();
        if (humanInput.fire3Down()) {
            Player selected = manualPlayerSwitcher.select(switchablePlayers, lastTrained, null);
            if (selected != null) {
                assignTrainingControl(selected, humanInput);
                lastTrained = selected;
            }
        }

        if (lastTrained.inputDevice.fire2Down()) {
            resetBall();
        }

    }

    @Override
    SceneFsm.Action[] checkConditions() {
        if (Gdx.input.isKeyPressed(ESCAPE)) {
            quitTraining();
            return null;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.R)) {
            return newFadedAction(HOLD_FOREGROUND, REPLAY);
        }

        return null;
    }

    private void resetBall() {
        ball.setX(lastTrained.x + BALL_R * EMath.cos(lastTrained.a));
        ball.setY(lastTrained.y + BALL_R * EMath.sin(lastTrained.a));
        ball.setZ(0);
        ball.vMax = 0;
    }

    private List<Player> switchablePlayers() {
        List<Player> players = new ArrayList<>();
        for (int t = HOME; t <= AWAY; t++) {
            players.addAll(team[t].lineup);
        }
        return players;
    }

    /** Transfers the training controller without changing the independently simulated ball. */
    private void assignTrainingControl(Player selected, InputDevice humanInput) {
        for (int t = HOME; t <= AWAY; t++) {
            for (Player player : team[t].lineup) {
                player.inputDevice = player == selected ? humanInput : player.ai;
            }
        }
        selected.setState(STATE_STAND_RUN);
    }

    private void setIntroPositions() {

        ball.setPosition(0, 0, 0);

        setIntroPositions(team[HOME]);
        setIntroPositions(team[AWAY]);

        team[HOME].coach.x = BENCH_X;
        team[HOME].coach.y = BENCH_Y_UP + 32;
    }

    private void setIntroPositions(Team team) {

        int len = team.lineup.size();
        for (int i = 0; i < len; i++) {
            Player player = team.lineup.get(i);

            Vector2 target = getDefaultTarget(player);
            player.x = target.x;
            player.y = target.y;
            player.z = 0;

            player.tx = target.x;
            player.ty = target.y;

            // set states
            if (player.role == GOALKEEPER && keepers[team.index] == null) {
                player.setState(STATE_KEEPER_POSITIONING);
                keepers[team.index] = player;
            } else {
                player.setState(STATE_STAND_RUN);
            }
        }
    }

    private Vector2 getDefaultTarget(Player player) {
        return new Vector2(
            -280 + 16 * (-player.team.lineup.size() + 2 * player.lineupIndex()) + 6 * EMath.cos(70 * (player.number)),
            -player.team.side * (150 + 5 * (player.lineupIndex() % 2)) + 8 * EMath.sin(70 * (player.number))
        );
    }

    private void resetTargetPosition(Player player) {
        Vector2 target = getDefaultTarget(player);
        player.tx = target.x;
        player.ty = target.y;
    }
}
