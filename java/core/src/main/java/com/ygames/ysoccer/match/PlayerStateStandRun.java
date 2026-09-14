package com.ygames.ysoccer.match;

import com.badlogic.gdx.math.Vector3;
import com.ygames.ysoccer.framework.EMath;

import static com.ygames.ysoccer.match.PlayerFsm.Id.STATE_STAND_RUN;

/**
 * Handles a player's normal standing, running, possession acquisition, and close dribbling.
 */
class PlayerStateStandRun extends PlayerState {

    private static final float BALL_LEAD_DISTANCE = 5.5f;
    private static final float TURNING_BALL_LEAD_DISTANCE = 2.5f;
    private static final float MIN_CONTROL_RESPONSE = 0.028f;
    private static final float CONTROL_RESPONSE_PER_SKILL = 0.002f;
    private static final float TURN_CONTROL_RESPONSE = 0.32f;
    private static final float MIN_POSITION_CORRECTION = 12f;
    private static final float POSITION_CORRECTION_PER_SKILL = 1f;
    private static final float MAX_DRIBBLE_SPEED_FACTOR = 1.1f;
    private static final float TURN_RECOVERY_SPEED_FACTOR = 0.15f;
    private static final float MAX_STANDING_CONTROL_SPEED = 35f;

    PlayerStateStandRun(PlayerFsm fsm) {
        super(STATE_STAND_RUN, fsm);
    }

    @Override
    void doActions() {
        super.doActions();

        if (ball.owner != player) {
            player.getPossession();
        }

        // Resolve movement before ball guidance so turns and stops affect the ball immediately.
        if (player.inputDevice.value) {
            player.v = player.speed
                * (1 - Const.POSSESSION_SPEED_PENALTY * ((player == ball.owner) ? 1 : 0));
            player.a = player.inputAngle();
        } else {
            player.v = 0;
        }

        if ((ball.owner == player)
            && (ball.z < Const.PLAYER_H)
            && (player.ballDistance < Const.DRIBBLE_CONTROL_DISTANCE)) {
            controlBall();
        }

        player.animationStandRun();
    }

    /**
     * Guides the independently simulated ball toward a point just ahead of the owner.
     * Blending velocity instead of forcing position preserves tackles, loose balls, pitch
     * friction, and player skill while avoiding the old repeated speed boosts.
     */
    private void controlBall() {
        float turnFactor = Math.min(
            1f,
            EMath.angleDiff(player.ballAngle, player.a) / 180f
        );
        float leadDistance = BALL_LEAD_DISTANCE
            - (BALL_LEAD_DISTANCE - TURNING_BALL_LEAD_DISTANCE) * turnFactor;
        float targetX = player.x + leadDistance * EMath.cos(player.a);
        float targetY = player.y + leadDistance * EMath.sin(player.a);
        float correction = MIN_POSITION_CORRECTION
            + POSITION_CORRECTION_PER_SKILL * player.skills.control;

        float targetVx = player.v * EMath.cos(player.a) + (targetX - ball.x) * correction;
        float targetVy = player.v * EMath.sin(player.a) + (targetY - ball.y) * correction;

        // Limit recovery speed so a badly displaced ball becomes loose instead of snapping back.
        float targetSpeed = EMath.hypo(targetVx, targetVy);
        float maxTargetSpeed = Math.max(
            MAX_STANDING_CONTROL_SPEED,
            (MAX_DRIBBLE_SPEED_FACTOR + TURN_RECOVERY_SPEED_FACTOR * turnFactor) * player.v
        );
        if (targetSpeed > maxTargetSpeed) {
            float scale = maxTargetSpeed / targetSpeed;
            targetVx *= scale;
            targetVy *= scale;
        }

        float ballVx = ball.v * EMath.cos(ball.a);
        float ballVy = ball.v * EMath.sin(ball.a);
        float response = MIN_CONTROL_RESPONSE
            + CONTROL_RESPONSE_PER_SKILL * player.skills.control
            + TURN_CONTROL_RESPONSE * turnFactor * turnFactor;

        ballVx += (targetVx - ballVx) * response;
        ballVy += (targetVy - ballVy) * response;
        ball.v = EMath.hypo(ballVx, ballVy);
        if (ball.v > 0.01f) {
            ball.a = EMath.aTan2(ballVy, ballVx);
        }
    }

    @Override
    State checkConditions() {

        if ((player.role == Player.Role.GOALKEEPER)
                && (player == player.team.lineup.get(0))
                && (player.team.near1 != player)
                && (player.inputDevice == player.ai)) {
            return fsm.stateKeeperPositioning;
        }

        // player fired
        if (player.inputDevice.fire1Down()) {

            // kick
            if (ball.owner == player) {
                if (player.v > 0 && ball.z < 8) {
                    player.kickAngle = player.a;
                    return fsm.stateKick;
                }
            }

            // head or tackle
            else if (player.ballDistance < 120 && player.ballIsApproaching()) {

                Vector3 ballPrediction = ball.prediction[Math.min(player.frameDistance, Const.BALL_PREDICTION - 1)];

                if (ballPrediction.z > 18) {
                    return fsm.stateHead;
                } else if (player.v > 0
                        && player.ballIsInFront()
                        && player.ballDistance > 12) {
                    return fsm.stateTackle;
                }
            }
            // A missed tackle request keeps control here; player hand-off belongs exclusively to fire3.
        }
        return null;
    }
}
