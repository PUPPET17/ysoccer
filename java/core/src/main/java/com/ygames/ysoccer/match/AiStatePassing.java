package com.ygames.ysoccer.match;

import com.ygames.ysoccer.framework.EMath;

import static com.ygames.ysoccer.framework.GLGame.VIRTUAL_REFRESH_RATE;
import static com.ygames.ysoccer.match.AiFsm.Id.STATE_PASSING;

class AiStatePassing extends AiState {

    private static final int TURNING_DURATION = 3;
    private static final int PRESS_DURATION = 8;
    private boolean tacticalTurn;

    AiStatePassing(AiFsm fsm) {
        super(STATE_PASSING, fsm);
    }

    @Override
    void entryActions() {
        super.entryActions();

        ai.fire10 = false;
        TacticalState tacticalState = player.team.getTacticalState();
        tacticalTurn = TacticalDecisionPolicy.usesTacticalPassing(
            tacticalState, tacticalState.getInstruction(player), player.team.getTeamPhase());
    }

    @Override
    void doActions() {
        super.doActions();

        if (tacticalTurn && player.passingMate != null) {
            float targetAngle = player.angleToPoint(player.passingMate.x, player.passingMate.y);
            ai.x0 = Math.round(EMath.cos(targetAngle));
            ai.y0 = Math.round(EMath.sin(targetAngle));
            player.passingMateAngleCorrection = EMath.signedAngleDiff(targetAngle, player.a);
        } else {
            ai.x0 = ai.x1;
            ai.y0 = ai.y1;
        }
        // Tactical side/back passes need a brief normal-control turn before the kick is charged.
        ai.fire10 = tacticalTurn
            ? timer > TURNING_DURATION && timer <= TURNING_DURATION + PRESS_DURATION
            : timer <= PRESS_DURATION;
    }

    @Override
    State checkConditions() {
        if (timer > VIRTUAL_REFRESH_RATE / 2) {
            return fsm.stateIdle;
        }

        return null;
    }
}
