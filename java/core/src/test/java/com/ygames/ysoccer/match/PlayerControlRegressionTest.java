package com.ygames.ysoccer.match;

import com.ygames.ysoccer.framework.Ai;
import com.ygames.ysoccer.framework.InputDevice;

import java.util.ArrayList;
import java.util.Arrays;

import static com.ygames.ysoccer.match.Player.Role.ATTACKER;
import static com.ygames.ysoccer.match.PlayerFsm.Id.STATE_STAND_RUN;

/** Regression checks for the separation between player actions and manual player switching. */
public final class PlayerControlRegressionTest {
    private static int checks;

    /** Runs the control hand-off checks without a graphics context or physical input device. */
    public static void main(String[] args) {
        tackleActionDoesNotSwitchPlayer();
        dedicatedSwitchActionChangesPlayer();
        System.out.println("Player control regression checks passed: " + checks);
    }

    /** A failed tackle attempt must leave control with the same player. */
    private static void tackleActionDoesNotSwitchPlayer() {
        Fixture fixture = new Fixture();
        fixture.input.fire10 = true;
        fixture.input.fire11 = false;

        fixture.controlled.fsm.stateStandRun.checkConditions();
        fixture.team.automaticInputDeviceSelection();

        check(fixture.controlled.inputDevice == fixture.input,
            "tackle action keeps the controlled player");
        check(fixture.candidate.inputDevice == fixture.candidate.ai,
            "tackle action does not hand control to the nearest teammate");
    }

    /** The third action remains the sole explicit path for cycling to a teammate. */
    private static void dedicatedSwitchActionChangesPlayer() {
        Fixture fixture = new Fixture();
        fixture.input.fire30 = true;
        fixture.input.fire31 = false;

        fixture.team.updateManualPlayerSwitch(true);

        check(fixture.controlled.inputDevice == fixture.controlled.ai,
            "switch action releases the previous player");
        check(fixture.candidate.inputDevice == fixture.input,
            "switch action assigns the selected teammate");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    /** Minimal open-play scene containing two eligible outfield players and one human input. */
    private static final class Fixture {
        final Match match = new Match();
        final Team team = new Team();
        final Player controlled = new Player();
        final Player candidate = new Player();
        final InputDevice input = new Ai(controlled);

        Fixture() {
            match.ball = new Ball(new SceneSettings());
            match.team[Match.HOME] = team;
            team.match = match;
            team.index = Match.HOME;
            team.controlMode = Team.ControlMode.PLAYER;
            team.inputDevice = input;
            team.lineup = new ArrayList<>(Arrays.asList(controlled, candidate));
            team.near1 = candidate;

            prepare(controlled, 2);
            prepare(candidate, 1);
            controlled.inputDevice = input;
        }

        private void prepare(Player player, int frameDistance) {
            player.team = team;
            player.scene = match;
            player.ball = match.ball;
            player.role = ATTACKER;
            player.isActive = true;
            player.ballDistance = 200;
            player.frameDistance = frameDistance;
            player.fsm = new PlayerFsm(player);
            player.fsm.setState(STATE_STAND_RUN);
        }
    }
}
