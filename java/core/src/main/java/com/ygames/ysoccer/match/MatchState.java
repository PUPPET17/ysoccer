package com.ygames.ysoccer.match;

import com.badlogic.gdx.Gdx;
import com.ygames.ysoccer.framework.InputDevice;

import static com.badlogic.gdx.Input.Keys.ESCAPE;
import static com.badlogic.gdx.Input.Keys.F1;
import static com.badlogic.gdx.Input.Keys.P;
import static com.badlogic.gdx.Input.Keys.R;
import static com.ygames.ysoccer.match.Match.AWAY;
import static com.ygames.ysoccer.match.Match.HOME;
import static com.ygames.ysoccer.match.MatchFsm.StateId.BENCH_ENTER;
import static com.ygames.ysoccer.match.MatchFsm.StateId.HELP;
import static com.ygames.ysoccer.match.MatchFsm.StateId.PAUSE;
import static com.ygames.ysoccer.match.MatchFsm.StateId.REPLAY;
import static com.ygames.ysoccer.match.SceneFsm.ActionType.HOLD_FOREGROUND;

abstract class MatchState extends SceneState<MatchFsm, Match> {

    boolean checkReplayKey = true;
    boolean checkPauseKey = true;
    boolean checkHelpKey = true;
    boolean checkBenchCall = true;

    final Ball ball;
    InputDevice inputDevice;
    int replayPosition;

    MatchState(MatchFsm.StateId state, MatchFsm matchFsm) {
        super(state, matchFsm);
        fsm.addState(this);
        this.ball = scene.ball;
    }

    @Override
    public MatchFsm.StateId getId() {
        return (MatchFsm.StateId) this.id;
    }

    SceneFsm.Action[] checkCommonConditions() {

        if (checkReplayKey && Gdx.input.isKeyPressed(R)) {
            return newFadedAction(HOLD_FOREGROUND, REPLAY);
        }

        if (checkPauseKey && (Gdx.input.isKeyPressed(P) || fsm.inputDevices.pauseDown())) {
            return newAction(HOLD_FOREGROUND, PAUSE);
        }

        if (checkHelpKey && Gdx.input.isKeyPressed(F1)) {
            return newAction(HOLD_FOREGROUND, HELP);
        }

        if (checkBenchCall) {
            for (int t = HOME; t <= AWAY; t++) {
                InputDevice inputDevice = scene.team[t].fire2Down();
                if (inputDevice != null) {
                    fsm.benchStatus.team = scene.team[t];
                    fsm.benchStatus.inputDevice = inputDevice;
                    return newAction(HOLD_FOREGROUND, BENCH_ENTER);
                }
            }
        }

        if (Gdx.input.isKeyPressed(ESCAPE)) {
            quitMatch();
            return null;
        }

        return null;
    }

    void quitMatch() {
        scene.quit();
    }
}
