package com.ygames.ysoccer.match;

import com.strongjoshua.console.CommandExecutor;
import com.strongjoshua.console.annotation.HiddenCommand;

public class ConsoleCommandExecutor extends CommandExecutor {

    @HiddenCommand
    public void gravity() {
        console.log("gravity " + Const.GRAVITY);
    }

    public void gravity(float f) {
        Const.GRAVITY = f;
    }

    @HiddenCommand
    public void airFriction() {
        console.log("airFriction " + Const.AIR_FRICTION);
    }

    public void airFriction(float f) {
        Const.AIR_FRICTION = f;
    }

    @HiddenCommand
    public void spinFactor() {
        console.log("spinFactor " + Const.SPIN_FACTOR);
    }

    public void spinFactor(float f) {
        Const.SPIN_FACTOR = f;
    }

    @HiddenCommand
    public void spinDampening() {
        console.log("spinDampening " + Const.SPIN_DAMPENING);
    }

    public void spinDampening(float f) {
        Const.SPIN_DAMPENING = f;
    }

    @HiddenCommand
    public void bounce() {
        console.log("bounce " + Const.BOUNCE);
    }

    public void bounce(float f) {
        Const.BOUNCE = f;
    }

    @HiddenCommand
    public void passingThreshold() {
        console.log("passingThreshold " + Const.PASSING_THRESHOLD);
    }

    public void passingThreshold(float f) {
        Const.PASSING_THRESHOLD = f;
    }

    @HiddenCommand
    public void passingSpeedFactor() {
        console.log("passingSpeedFactor " + Const.PASSING_SPEED_FACTOR);
    }

    public void passingSpeedFactor(float f) {
        Const.PASSING_SPEED_FACTOR = f;
    }

    /** Prints all live dribbling values together with the unmodified game's values. */
    public void dribbling() {
        dribbleControlDistance();
        ballOwnerReleaseDistance();
        possessionSpeedPenalty();
    }

    @HiddenCommand
    public void dribbleControlDistance() {
        console.log("dribbleControlDistance current=" + Const.DRIBBLE_CONTROL_DISTANCE
            + ", original=" + Const.ORIGINAL_DRIBBLE_CONTROL_DISTANCE);
    }

    /**
     * Changes the close-control radius for the running game session.
     *
     * @param value distance in pitch pixels; must be positive and below the release distance
     */
    public void dribbleControlDistance(float value) {
        if (value <= 0 || value >= Const.BALL_OWNER_RELEASE_DISTANCE) {
            console.log("Value must be > 0 and < ballOwnerReleaseDistance ("
                + Const.BALL_OWNER_RELEASE_DISTANCE + ")");
            return;
        }
        Const.DRIBBLE_CONTROL_DISTANCE = value;
        dribbleControlDistance();
    }

    @HiddenCommand
    public void ballOwnerReleaseDistance() {
        console.log("ballOwnerReleaseDistance current=" + Const.BALL_OWNER_RELEASE_DISTANCE
            + ", original=" + Const.ORIGINAL_BALL_OWNER_RELEASE_DISTANCE);
    }

    /**
     * Changes how far the ball may move from its owner before possession is released.
     *
     * @param value distance in pitch pixels; must remain above the close-control radius
     */
    public void ballOwnerReleaseDistance(float value) {
        if (value <= Const.DRIBBLE_CONTROL_DISTANCE) {
            console.log("Value must be > dribbleControlDistance ("
                + Const.DRIBBLE_CONTROL_DISTANCE + ")");
            return;
        }
        Const.BALL_OWNER_RELEASE_DISTANCE = value;
        ballOwnerReleaseDistance();
    }

    @HiddenCommand
    public void possessionSpeedPenalty() {
        console.log("possessionSpeedPenalty current=" + Const.POSSESSION_SPEED_PENALTY
            + " (" + percentage(Const.POSSESSION_SPEED_PENALTY) + "%), original="
            + Const.ORIGINAL_POSSESSION_SPEED_PENALTY + " ("
            + percentage(Const.ORIGINAL_POSSESSION_SPEED_PENALTY) + "%)");
    }

    /**
     * Changes the fractional running-speed reduction applied to the ball owner.
     *
     * @param value fraction from 0 (no penalty) through 0.5 (50 percent penalty)
     */
    public void possessionSpeedPenalty(float value) {
        if (value < 0 || value > 0.5f) {
            console.log("Value must be between 0 and 0.5");
            return;
        }
        Const.POSSESSION_SPEED_PENALTY = value;
        possessionSpeedPenalty();
    }

    private String percentage(float value) {
        return Float.toString(value * 100f);
    }
}
