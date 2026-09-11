package com.ygames.ysoccer.match;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.ygames.ysoccer.framework.InputDevice;

import static com.ygames.ysoccer.match.MatchFsm.StateId.PAUSE;
import static com.ygames.ysoccer.match.MatchFsm.StateId.REPLAY;
import static com.ygames.ysoccer.match.SceneFsm.ActionType.NEW_FOREGROUND;
import static com.ygames.ysoccer.match.SceneFsm.ActionType.RESTORE_FOREGROUND;

class MatchStatePause extends MatchState {

    private boolean keyPause;
    private boolean waitingNoPauseKey;
    private boolean resume;

    MatchStatePause(MatchFsm fsm) {
        super(PAUSE, fsm);

        checkReplayKey = false;
        checkPauseKey = false;
        checkHelpKey = false;
        checkBenchCall = false;
    }

    @Override
    void setDisplayFlags() {
        scene.displayPause = true;
    }

    @Override
    void entryActions() {
        super.entryActions();

        keyPause = Gdx.input.isKeyPressed(Input.Keys.P);
        waitingNoPauseKey = true;
        resume = false;
    }

    @Override
    void doActions(float deltaTime) {
        super.doActions(deltaTime);

        // resume on 'P'
        if (waitingNoPauseKey) {
            if (!keyPause) {
                waitingNoPauseKey = false;
            }
        } else if (!Gdx.input.isKeyPressed(Input.Keys.P) && keyPause) {
            resume = true;
        }
        keyPause = Gdx.input.isKeyPressed(Input.Keys.P);
    }

    @Override
    SceneFsm.Action[] checkConditions() {

        if (resume || fsm.inputDevices.pauseDown()) {
            return newAction(RESTORE_FOREGROUND);
        }

        // resume on fire button
        for (InputDevice d : fsm.inputDevices) {
            if (d.fire1Down()) {
                return newAction(RESTORE_FOREGROUND);
            }
        }

        // hand over to replay
        if (Gdx.input.isKeyPressed(Input.Keys.R)) {
            return newFadedAction(NEW_FOREGROUND, REPLAY);
        }

        return checkCommonConditions();
    }
}
