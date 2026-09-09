package com.ygames.ysoccer.match;

import com.badlogic.gdx.Gdx;
import com.ygames.ysoccer.events.CrowdChantsEvent;
import com.ygames.ysoccer.framework.Assets;
import com.ygames.ysoccer.framework.EMath;
import com.ygames.ysoccer.framework.EventManager;
import com.ygames.ysoccer.framework.GLGame;

import static com.ygames.ysoccer.match.Const.TEAM_SIZE;
import static com.ygames.ysoccer.match.Match.AWAY;
import static com.ygames.ysoccer.match.Match.HOME;
import static com.ygames.ysoccer.match.MatchFsm.StateId.CORNER_STOP;
import static com.ygames.ysoccer.match.MatchFsm.StateId.EXTRA_TIME_STOP;
import static com.ygames.ysoccer.match.MatchFsm.StateId.FREE_KICK_STOP;
import static com.ygames.ysoccer.match.MatchFsm.StateId.FULL_EXTRA_TIME_STOP;
import static com.ygames.ysoccer.match.MatchFsm.StateId.FULL_TIME_STOP;
import static com.ygames.ysoccer.match.MatchFsm.StateId.GOAL;
import static com.ygames.ysoccer.match.MatchFsm.StateId.GOAL_KICK_STOP;
import static com.ygames.ysoccer.match.MatchFsm.StateId.HALF_EXTRA_TIME_STOP;
import static com.ygames.ysoccer.match.MatchFsm.StateId.HALF_TIME_STOP;
import static com.ygames.ysoccer.match.MatchFsm.StateId.KEEPER_STOP;
import static com.ygames.ysoccer.match.MatchFsm.StateId.MAIN;
import static com.ygames.ysoccer.match.MatchFsm.StateId.PENALTIES_STOP;
import static com.ygames.ysoccer.match.MatchFsm.StateId.PENALTY_KICK_STOP;
import static com.ygames.ysoccer.match.MatchFsm.StateId.RED_CARD;
import static com.ygames.ysoccer.match.MatchFsm.StateId.THROW_IN_STOP;
import static com.ygames.ysoccer.match.MatchFsm.StateId.YELLOW_CARD;
import static com.ygames.ysoccer.match.PlayerFsm.Id.STATE_DOWN;
import static com.ygames.ysoccer.match.PlayerFsm.Id.STATE_TACKLE;
import static com.ygames.ysoccer.match.SceneFsm.ActionType.NEW_FOREGROUND;

class MatchStateMain extends MatchState {

    private enum Event {
        KEEPER_STOP, GOAL, CORNER, GOAL_KICK, THROW_IN, FREE_KICK, PENALTY_KICK, YELLOW_CARD, RED_CARD, NONE
    }

    private Event event;

    MatchStateMain(MatchFsm fsm) {
        super(MAIN, fsm);

        checkBenchCall = false;
    }

    @Override
    void setDisplayFlags() {
        scene.clearDisplayFlags();
        scene.displayControlledPlayer = true;
        scene.displayBallOwner = true;
        scene.displayTime = true;
        scene.displayRadar = true;
        scene.displayWindVane = true;
    }

    @Override
    void entryActions() {
        super.entryActions();

        event = Event.NONE;
    }

    @Override
    void doActions(float deltaTime) {
        super.doActions(deltaTime);

        float timeLeft = deltaTime;
        boolean processSwitchButton = true;
        while (timeLeft >= GLGame.SUBFRAME_DURATION) {

            if (scene.subframe % GLGame.SUBFRAMES == 0) {
                scene.updateAi();

                // crowd chants
                if (scene.clock >= scene.nextChant) {
                    if (scene.chantSwitch) {
                        scene.chantSwitch = false;
                        scene.nextChant = scene.clock + (6 + Assets.random.nextInt(6)) * 1000;
                    } else {
                        EventManager.publish(new CrowdChantsEvent());
                        scene.chantSwitch = true;
                        scene.nextChant = scene.clock + 8000;
                    }
                }

                scene.clock += 1000.0f / GLGame.VIRTUAL_REFRESH_RATE;

                scene.updateFrameDistance();

                if (scene.ball.owner != null) {
                    scene.stats[scene.ball.owner.team.index].ballPossession += 1;
                }
            }

            scene.updateBall();

            int attackingTeam = scene.attackingTeam();
            int defendingTeam = 1 - attackingTeam;

            if (scene.ball.holder != null) {
                event = Event.KEEPER_STOP;
                return;
            }

            scene.ball.collisionFlagPosts();

            if (scene.ball.collisionGoal()) {
                float elapsed = scene.clock - scene.lastGoalCollisionTime;
                if (elapsed > 100) {
                    scene.stats[attackingTeam].overallShots += 1;
                    scene.stats[attackingTeam].centeredShots += 1;
                    scene.lastGoalCollisionTime = scene.clock;
                }
            }

            // goal/corner/goal-kick
            if (scene.ball.y * scene.ball.ySide >= (Const.GOAL_LINE + Const.BALL_R)) {
                // goal
                if (EMath.isIn(scene.ball.x, -Const.POST_X, Const.POST_X)
                    && (scene.ball.z <= Const.CROSSBAR_H)) {
                    scene.stats[attackingTeam].goals += 1;
                    scene.stats[attackingTeam].overallShots += 1;
                    scene.stats[attackingTeam].centeredShots += 1;
                    scene.addGoal(attackingTeam);

                    event = Event.GOAL;
                    return;
                } else {
                    // corner/goal-kick
                    if (scene.ball.ownerLast.team == scene.team[defendingTeam]) {
                        event = Event.CORNER;
                        fsm.cornerKickTeam = scene.team[1 - scene.ball.ownerLast.team.index];
                        return;
                    } else {
                        if (EMath.isIn(scene.ball.x, -Const.GOAL_AREA_W / 2f, Const.GOAL_AREA_W / 2f)) {
                            scene.stats[attackingTeam].overallShots += 1;
                        }
                        event = Event.GOAL_KICK;
                        fsm.goalKickTeam = scene.team[1 - scene.ball.ownerLast.team.index];
                        return;
                    }
                }
            }

            // throw-ins
            if (Math.abs(scene.ball.x) > (Const.TOUCH_LINE + Const.BALL_R)) {
                event = Event.THROW_IN;
                fsm.throwInTeam = scene.team[1 - scene.ball.ownerLast.team.index];
                return;
            }

            // colliding tackles and fouls
            if (scene.tackle == null) {

                for (int t = HOME; t <= AWAY; t++) {

                    // for each tackling player
                    for (int i = 0; i < TEAM_SIZE; i++) {
                        Player player = scene.team[t].lineup.get(i);
                        if (player != null && player.checkState(STATE_TACKLE) && player.v > 50) {

                            // search near opponents
                            Team opponentTeam = scene.team[1 - player.team.index];
                            Player opponent = opponentTeam.searchPlayerTackledBy(player);

                            if (opponent != null && !opponent.checkState(STATE_DOWN)) {
                                float strength = (4f + player.v / 260f) / 5f;
                                float angleDiff = EMath.angleDiff(player.a, opponent.a);
                                scene.newTackle(player, opponent, strength, angleDiff);
                                Gdx.app.debug(player.shirtName, "tackles on " + opponent.shirtName + " at speed: " + player.v + " (strength = " + strength + ") and angle: " + angleDiff);
                            }
                        }
                    }
                }
            } else {
                if (EMath.dist(scene.tackle.player.x, scene.tackle.player.y, scene.tackle.opponent.x, scene.tackle.opponent.y) >= 8) {

                    // tackle is finished, eventually generate a foul
                    Player player = scene.tackle.player;
                    Player opponent = scene.tackle.opponent;
                    float angleDiff = scene.tackle.angleDiff;

                    float hardness;

                    // back/side
                    if (angleDiff < 112.5f) {
                        hardness = scene.tackle.strength * (0.7f + 0.01f * player.skills.tackling - 0.01f * opponent.skills.control);
                    }

                    // front
                    else {
                        hardness = scene.tackle.strength * (0.9f + 0.01f * player.skills.tackling - 0.01f * opponent.skills.control);
                    }

                    float unfairness;

                    // back tackle
                    if (angleDiff < 67.5f) {
                        unfairness = (player.ballDistance < opponent.ballDistance) ? 0.8f : 0.9f;
                    }

                    // side tackle
                    else if (angleDiff < 112.5f) {
                        unfairness = (player.ballDistance < opponent.ballDistance) ? 0.2f : 0.8f;
                    }

                    // front tackle
                    else {
                        unfairness = (player.ballDistance < opponent.ballDistance) ? 0.3f : 0.9f;
                    }

                    Gdx.app.debug(player.shirtName, "tackles on " + opponent.shirtName + " finished, hardness: " + hardness + ", unfairness: " + unfairness);

                    if (Assets.random.nextFloat() < hardness) {
                        opponent.setState(STATE_DOWN);

                        if (Assets.random.nextFloat() < unfairness) {
                            scene.newFoul(scene.tackle.opponent.x, scene.tackle.opponent.y, hardness, unfairness);
                            Gdx.app.debug(player.shirtName, "tackle on " + opponent.shirtName + " is a foul at: " + scene.tackle.opponent.x + ", " + scene.tackle.opponent.y
                                + " direct shot: " + (scene.foul.isDirectShot() ? "yes" : "no") + " yellow: " + scene.foul.entailsYellowCard + " red: " + scene.foul.entailsRedCard);
                        } else {
                            Gdx.app.debug(player.shirtName, "tackles on " + opponent.shirtName + " is probably not a foul");
                        }
                    } else {
                        Gdx.app.debug(opponent.shirtName, "avoids the tackle from " + player.shirtName);
                    }
                    scene.tackle = null;
                }
            }

            if (scene.foul != null) {
                if (scene.foul.entailsRedCard) {
                    scene.referee.addRedCard(scene.foul.player);
                    scene.stats[scene.foul.player.team.index].redCards++;

                    event = Event.RED_CARD;
                } else if (scene.foul.entailsYellowCard) {
                    scene.referee.addYellowCard(scene.foul.player);
                    scene.stats[scene.foul.player.team.index].yellowCards++;
                    if (scene.referee.isSentOff(scene.foul.player)) {
                        scene.stats[scene.foul.player.team.index].redCards++;
                    }

                    event = Event.YELLOW_CARD;
                } else if (scene.foul.isPenalty()) {
                    event = Event.PENALTY_KICK;
                } else {
                    event = Event.FREE_KICK;
                }
                scene.stats[scene.foul.player.team.index].foulsConceded += 1;
                return;
            }

            scene.updatePlayers(true);
            scene.findNearest();

            for (int t = HOME; t <= AWAY; t++) {
                if (scene.team[t].usesAutomaticInputDevice()) {
                    scene.team[t].updateManualPlayerSwitch(processSwitchButton);
                    scene.team[t].automaticInputDeviceSelection();
                }
            }
            // Input edges are sampled once per rendered frame, while this loop may run eight or more physics steps.
            processSwitchButton = false;

            scene.updateBallZone();
            scene.updateTeamTactics();

            if ((scene.subframe % GLGame.SUBFRAMES) == 0) {
                scene.ball.updatePrediction();
            }

            scene.nextSubframe();

            scene.save();

            scene.camera.update();

            timeLeft -= GLGame.SUBFRAME_DURATION;
        }
    }

    @Override
    SceneFsm.Action[] checkConditions() {
        switch (event) {
            case KEEPER_STOP:
                return newAction(NEW_FOREGROUND, KEEPER_STOP);

            case GOAL:
                return newAction(NEW_FOREGROUND, GOAL);

            case CORNER:
                return newAction(NEW_FOREGROUND, CORNER_STOP);

            case GOAL_KICK:
                return newAction(NEW_FOREGROUND, GOAL_KICK_STOP);

            case THROW_IN:
                return newAction(NEW_FOREGROUND, THROW_IN_STOP);

            case RED_CARD:
                return newAction(NEW_FOREGROUND, RED_CARD);

            case YELLOW_CARD:
                return newAction(NEW_FOREGROUND, YELLOW_CARD);

            case FREE_KICK:
                return newAction(NEW_FOREGROUND, FREE_KICK_STOP);

            case PENALTY_KICK:
                return newAction(NEW_FOREGROUND, PENALTY_KICK_STOP);
        }

        switch (scene.period) {

            case UNDEFINED:
                break;

            case FIRST_HALF:
                if ((scene.clock > (scene.length * 45f / 90f)) && scene.periodIsTerminable()) {
                    return newAction(NEW_FOREGROUND, HALF_TIME_STOP);
                }
                break;

            case SECOND_HALF:
                if ((scene.clock > scene.length) && scene.periodIsTerminable()) {

                    scene.setResult(scene.stats[HOME].goals, scene.stats[AWAY].goals, Match.ResultType.AFTER_90_MINUTES);

                    if (scene.competition.playExtraTime()) {
                        return newAction(NEW_FOREGROUND, EXTRA_TIME_STOP);
                    } else if (scene.competition.playPenalties()) {
                        return newAction(NEW_FOREGROUND, PENALTIES_STOP);
                    } else {
                        return newAction(NEW_FOREGROUND, FULL_TIME_STOP);
                    }
                }
                break;

            case FIRST_EXTRA_TIME:
                if ((scene.clock > (scene.length * 105f / 90f)) && scene.periodIsTerminable()) {
                    return newAction(NEW_FOREGROUND, HALF_EXTRA_TIME_STOP);
                }
                break;

            case SECOND_EXTRA_TIME:
                if ((scene.clock > (scene.length * 120f / 90f)) && scene.periodIsTerminable()) {

                    scene.setResult(scene.stats[HOME].goals, scene.stats[AWAY].goals, Match.ResultType.AFTER_EXTRA_TIME);

                    if (scene.competition.playPenalties()) {
                        return newAction(NEW_FOREGROUND, PENALTIES_STOP);
                    } else {
                        return newAction(NEW_FOREGROUND, FULL_EXTRA_TIME_STOP);
                    }
                }
                break;
        }

        return checkCommonConditions();
    }
}
