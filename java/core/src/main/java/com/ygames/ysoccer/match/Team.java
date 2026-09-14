package com.ygames.ysoccer.match;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.ygames.ysoccer.framework.Assets;
import com.ygames.ysoccer.framework.EMath;
import com.ygames.ysoccer.framework.GLGame;
import com.ygames.ysoccer.framework.InputDevice;
import com.ygames.ysoccer.framework.RgbPair;
import com.ygames.ysoccer.framework.Settings;
import com.ygames.ysoccer.framework.TeamList;
import com.ygames.ysoccer.gui.Gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ygames.ysoccer.match.Const.FREE_KICK_DISTANCE;
import static com.ygames.ysoccer.match.Const.GOAL_LINE;
import static com.ygames.ysoccer.match.Const.PLAYER_W;
import static com.ygames.ysoccer.match.Const.POST_X;
import static com.ygames.ysoccer.match.Const.TEAM_SIZE;
import static com.ygames.ysoccer.match.Player.Role.ATTACKER;
import static com.ygames.ysoccer.match.Player.Role.DEFENDER;
import static com.ygames.ysoccer.match.Player.Role.GOALKEEPER;
import static com.ygames.ysoccer.match.Player.Role.LEFT_BACK;
import static com.ygames.ysoccer.match.Player.Role.LEFT_WINGER;
import static com.ygames.ysoccer.match.Player.Role.MIDFIELDER;
import static com.ygames.ysoccer.match.Player.Role.RIGHT_BACK;
import static com.ygames.ysoccer.match.Player.Role.RIGHT_WINGER;

public class Team implements Json.Serializable {

    public enum Type {CLUB, NATIONAL, CUSTOM}

    public enum ControlMode {UNDEFINED, COMPUTER, PLAYER, COACH}

    private final int[] controlModeColors = {0x666666, 0x981E1E, 0x123FC8, 0x009BDC};

    public Match match;

    public String name;
    public Type type;
    public String path;
    public String country;
    private String confederation;
    public String league;
    public String city;
    public String stadium;
    public Coach coach;

    public int tactics;

    public int kitIndex;
    public List<Kit> kits;
    public static final int MIN_KITS = 3;
    public static final int MAX_KITS = 5;

    public int index; // 0=HOME, 1=AWAY

    public List<Player> players;
    public List<Player> lineup;
    int substitutionsCount;
    int kickerIndex;
    ArrayList<Player> barrier;

    private final static Map<Player.Role, Player.Role[]> substitutionRules;

    static {
        Map<Player.Role, Player.Role[]> aMap = new HashMap<>();
        aMap.put(GOALKEEPER, new Player.Role[]{GOALKEEPER, GOALKEEPER});
        aMap.put(RIGHT_BACK, new Player.Role[]{LEFT_BACK, DEFENDER});
        aMap.put(LEFT_BACK, new Player.Role[]{RIGHT_BACK, DEFENDER});
        aMap.put(DEFENDER, new Player.Role[]{RIGHT_BACK, LEFT_BACK});
        aMap.put(RIGHT_WINGER, new Player.Role[]{LEFT_WINGER, MIDFIELDER});
        aMap.put(LEFT_WINGER, new Player.Role[]{RIGHT_WINGER, MIDFIELDER});
        aMap.put(MIDFIELDER, new Player.Role[]{RIGHT_WINGER, LEFT_WINGER});
        aMap.put(ATTACKER, new Player.Role[]{RIGHT_WINGER, LEFT_WINGER});
        substitutionRules = Collections.unmodifiableMap(aMap);
    }

    public ControlMode controlMode;
    public InputDevice inputDevice;
    int side; // -1=upside, 1=downside

    Player near1; // nearest to the ball
    Player bestDefender;
    private final ManualPlayerSwitcher manualPlayerSwitcher;
    private final TacticalState tacticalState;
    private final TacticalDebugMetrics tacticalDebugMetrics;
    private final TeamPhaseTracker teamPhaseTracker;

    public TextureRegion image;
    public boolean imageIsDefaultLogo;

    public Team() {
        controlMode = ControlMode.UNDEFINED;
        kits = new ArrayList<>();
        players = new ArrayList<>();
        manualPlayerSwitcher = new ManualPlayerSwitcher();
        tacticalState = new TacticalState();
        tacticalDebugMetrics = new TacticalDebugMetrics();
        teamPhaseTracker = new TeamPhaseTracker();
    }

    @Override
    public void read(Json json, JsonValue jsonData) {
        name = jsonData.getString("name");
        path = jsonData.getString("path", null);
        controlMode = json.readValue("controlMode", ControlMode.class, ControlMode.UNDEFINED, jsonData);
        type = json.readValue("type", Team.Type.class, jsonData);
        country = jsonData.getString("country");
        confederation = jsonData.getString("confederation", null);
        league = jsonData.getString("league", null);
        city = jsonData.getString("city");
        stadium = jsonData.getString("stadium");

        coach = json.readValue("coach", Coach.class, jsonData);
        coach.teamIndex = this.index;

        String tacticsCode = jsonData.getString("tactics");
        tactics = 0;
        for (int i = 0; i < Tactics.codes.length; i++) {
            if (Tactics.codes[i].equals(tacticsCode)) {
                tactics = i;
            }
        }

        Kit[] kitsArray = json.readValue("kits", Kit[].class, jsonData);
        Collections.addAll(kits, kitsArray);

        Player[] playersArray = json.readValue("players", Player[].class, jsonData);
        for (Player player : playersArray) {
            player.team = this;
            players.add(player);
        }
    }

    @Override
    public void write(Json json) {
        json.writeValue("name", name);
        if (path != null) {
            json.writeValue("path", path);
        }
        if (controlMode != ControlMode.UNDEFINED) {
            json.writeValue("controlMode", controlMode);
        }
        json.writeValue("type", type);
        json.writeValue("country", country);
        if (confederation != null) {
            json.writeValue("confederation", confederation);
        }
        if (league != null) {
            json.writeValue("league", league);
        }
        json.writeValue("city", city);
        json.writeValue("stadium", stadium);
        json.writeValue("coach", coach);
        json.writeValue("tactics", Tactics.codes[tactics]);
        json.writeValue("kits", kits, Kit[].class, Kit.class);
        json.writeValue("players", players, Player[].class, Player.class);
    }

    public void setInputDevice(InputDevice inputDevice) {
        this.inputDevice = inputDevice;
    }

    void setSide(int side) {
        this.side = side;
        for (Player player : lineup) {
            player.side = side;
        }
    }

    public Player newPlayer() {
        if (players.size() == Const.FULL_TEAM) {
            return null;
        }

        Player player = new Player();
        player.name = "";
        player.shirtName = "";
        player.nationality = "";
        player.role = GOALKEEPER;
        rotatePlayerNumber(player, 1);
        player.skinColor = Skin.Color.PINK;
        player.hairColor = Hair.Color.BLACK;
        player.hairStyle = "SMOOTH_A";
        player.skills = new Player.Skills();
        players.add(player);
        return player;
    }

    void beforeMatch(Match match) {
        this.match = match;
        tacticalState.resetImmediately();
        tacticalDebugMetrics.reset();
        teamPhaseTracker.reset();
        lineup = new ArrayList<>();
        int lineupSize = Math.min(players.size(), TEAM_SIZE + match.getSettings().benchSize);
        for (int i = 0; i < lineupSize; i++) {
            Player player = players.get(i);
            player.beforeMatch(match);
            lineup.add(player);
        }
        substitutionsCount = 0;
        kickerIndex = TEAM_SIZE - 1;
        barrier = new ArrayList<>();
    }

    void beforeTraining(Training training) {
        // Training has no coach-command lifecycle and must not inherit a previous match profile.
        match = null;
        tacticalState.resetImmediately();
        tacticalDebugMetrics.reset();
        teamPhaseTracker.reset();
        lineup = new ArrayList<>();
        int lineupSize = players.size();
        for (int i = 0; i < lineupSize; i++) {
            Player player = players.get(i);
            player.beforeTraining(training);
            lineup.add(player);
        }
    }

    void save(int subframe) {
        int len = lineup.size();
        for (int i = 0; i < len; i++) {
            lineup.get(i).save(subframe);
        }
    }

    void updateCurrentData(int subframe) {
        int len = lineup.size();
        for (int i = 0; i < len; i++) {
            lineup.get(i).updateCurrentData(subframe);
        }
    }

    void findNearest() {
        findNearest(TEAM_SIZE);
    }

    void findNearest(int count) {
        // step 1: search using frame_distance,
        // which takes into account both the speed of the player and the speed
        // and direction of the ball
        near1 = null;
        for (int i = 0; i < count; i++) {
            Player player = lineup.get(i);

            // discard those players which cannot reach the ball in less than
            // BALL_PREDICTION frames
            if (player.frameDistance == Const.BALL_PREDICTION)
                continue;

            if ((near1 == null) || (player.frameDistance < near1.frameDistance)) {
                near1 = player;
            }
        }

        // step 2: if not found, repeat using pixel distance
        if (near1 == null) {
            near1 = lineup.get(0);
            for (int i = 1; i < count; i++) {
                Player player = lineup.get(i);

                if (player.ballDistance < near1.ballDistance) {
                    near1 = player;
                }
            }
        }
    }

    private void findBestDefender() {
        Player newBestDefender = null;

        float minDefenderDistance = 2 * Const.GOAL_LINE;
        if ((match.ball.ownerLast != null) && (match.ball.ownerLast.team != this)) {
            float ballToGoalDistance = EMath.dist(match.ball.x, match.ball.y, 0, Const.GOAL_LINE * side);

            for (int i = 1; i < TEAM_SIZE; i++) {
                Player player = lineup.get(i);

                float playerGoalDistance = EMath.dist(player.x, player.y, 0, Const.GOAL_LINE * side);
                float effectiveDistance = TacticalDecisionPolicy.effectiveDefenderDistance(
                    player.ballDistance, tacticalState.getInstruction(player), getTeamPhase());
                if ((playerGoalDistance < 0.95f * ballToGoalDistance)
                    && (effectiveDistance < minDefenderDistance)) {
                    newBestDefender = player;
                    minDefenderDistance = effectiveDistance;
                }
            }
        }

        float currentDefenderDistance = bestDefender == null
            ? Float.MAX_VALUE
            : TacticalDecisionPolicy.effectiveDefenderDistance(
                bestDefender.ballDistance, tacticalState.getInstruction(bestDefender),
                getTeamPhase());
        if (bestDefender == null || minDefenderDistance < 0.9f * currentDefenderDistance) {
            bestDefender = newBestDefender;
        }
    }

    void updateTactics(boolean relativeToCenter) {
        int ball_zone = 17 - side * match.ball.zoneX - 5 * side * match.ball.zoneY;
        if (relativeToCenter) {
            ball_zone = 17;
        }

        int activePlayers = Math.min(TEAM_SIZE, lineup.size());
        if (activePlayers <= 1) return;

        float centreX = 0;
        float centreY = 0;
        for (int i = 1; i < activePlayers; i++) {
            float baseX = -side * Assets.tactics[tactics].target[i][ball_zone][0];
            float baseY = -side * (Assets.tactics[tactics].target[i][ball_zone][1] - 4);
            centreX += baseX;
            centreY += baseY;
        }
        centreX /= activePlayers - 1;
        centreY /= activePlayers - 1;

        TeamPhase phase = getTeamPhase();

        for (int i = 1; i < activePlayers; i++) {
            Player player = lineup.get(i);
            float baseX = -side * Assets.tactics[tactics].target[i][ball_zone][0];
            float baseY = -side * (Assets.tactics[tactics].target[i][ball_zone][1] - 4);
            PlayerTacticalInstruction instruction = tacticalState.getInstruction(player);
            float targetX = TacticalPositioning.targetX(
                baseX, centreX, match.ball.x, phase, tacticalState, instruction);
            float targetY = TacticalPositioning.targetY(
                baseX, baseY, centreY, side, match.ball.x, match.ball.y,
                phase, tacticalState, instruction);
            player.setTarget(targetX, targetY);
        }
    }

    void keepTargetDistanceFrom(Vector2 position) {
        for (int i = 1; i < TEAM_SIZE; i++) {
            Player player = lineup.get(i);
            Vector2 vec = new Vector2(player.tx, player.ty);
            vec.sub(position);
            if (vec.len() < FREE_KICK_DISTANCE) {
                vec.setLength(FREE_KICK_DISTANCE);
                vec.add(position);
                player.setTarget(vec.x, vec.y);
            }
        }
    }

    void setFreeKickBarrier() {
        barrier.clear();

        // angle to cover
        float nearPostAngle = EMath.angle(match.foul.position.x, match.foul.position.y, Math.signum(match.foul.position.x) * POST_X, side * GOAL_LINE);
        float goalCenterAngle = EMath.angle(match.foul.position.x, match.foul.position.y, 0, side * GOAL_LINE);
        float angleToCover = EMath.signedAngleDiff(goalCenterAngle, nearPostAngle);

        // angle step
        Vector2 foulToGoal = new Vector2(0, side * GOAL_LINE).sub(match.foul.position);
        Vector2 foulToBarrier = new Vector2(foulToGoal).setLength(FREE_KICK_DISTANCE + Const.PLAYER_W / 2f);
        float angleStep = EMath.aTan2(Const.PLAYER_W, foulToBarrier.len()) * Math.signum(angleToCover);

        // barrier size
        int size = EMath.ceil(angleToCover / angleStep);

        while (barrier.size() < size) {

            // search nearest player among those beyond barrier position
            float minDistance = 2 * Const.GOAL_LINE;
            float goalToBarrierDistance = foulToGoal.len() - foulToBarrier.len();
            Player nearest = null;
            for (int i = 1; i < TEAM_SIZE; i++) {
                Player player = lineup.get(i);
                if (!barrier.contains(player)) {
                    float goalDistance = EMath.dist(player.tx, player.ty, 0, player.team.side * GOAL_LINE);
                    if ((goalDistance > goalToBarrierDistance) && (goalDistance < minDistance)) {
                        nearest = player;
                        minDistance = goalDistance;
                    }
                }
            }

            if (nearest != null) {
                Gdx.app.debug(nearest.shirtName, "added to barrier");
                barrier.add(nearest);
            } else {
                break;
            }
        }

        // set targets
        float barrierAngle = EMath.angle(match.foul.position.x, match.foul.position.y, Math.signum(match.foul.position.x) * POST_X / 2f, side * GOAL_LINE);
        Vector2 barrierCenter = new Vector2(1, 1)
            .setLength(FREE_KICK_DISTANCE + Const.PLAYER_W / 2f)
            .setAngle(barrierAngle)
            .add(match.foul.position.x, match.foul.position.y);
        for (int i = 0; i < barrier.size(); i++) {
            float offset = i - (barrier.size() - 1) / 2f;
            Vector2 barrierPosition = new Vector2(1, 1)
                .setLength(Math.abs(offset) * PLAYER_W)
                .setAngle(barrierAngle + 90 * Math.signum(offset))
                .add(barrierCenter);
            barrier.get(i).setTarget(barrierPosition.x, barrierPosition.y);
        }
    }

    void updateFrameDistance() {
        updateFrameDistance(TEAM_SIZE);
    }

    void updateFrameDistance(int count) {
        for (int i = 0; i < count; i++) {
            lineup.get(i).updateFrameDistance();
        }
    }

    boolean updatePlayers(boolean limit) {
        findNearest();

        // TODO: move to the main update loop
        if (match.subframe % GLGame.SUBFRAMES == 0) findBestDefender();

        return updateLineup(limit);
    }

    boolean updateLineup(boolean limit) {
        boolean move = false;

        int len = lineup.size();
        for (int i = 0; i < len; i++) {
            Player player = lineup.get(i);
            if (player.update(limit)) {
                move = true;
            }
            player.think();
        }

        return move;
    }

    void updateLineupAi() {
        // Phase evidence and coach intent advance at the same 64 Hz cadence as AI decisions.
        teamPhaseTracker.update(this, match == null ? null : match.ball);
        tacticalState.update();
        if (Settings.development) tacticalDebugMetrics.sample(this);
        int len = lineup.size();
        for (int i = 0; i < len; i++) {
            Player player = lineup.get(i);
            if (player.inputDevice == player.ai) {
                player.updateAi();
            }
        }
    }

    /**
     * Returns this team's match-local coach intent. The state biases normal AI decisions and is
     * reset before each match; it is deliberately excluded from team-data serialization.
     */
    public TacticalState getTacticalState() {
        return tacticalState;
    }

    /**
     * Returns the debounced match phase from this team's perspective. The value is derived solely
     * from simulation-frame ownership evidence and is reset for each match or training session.
     */
    public TeamPhase getTeamPhase() {
        return teamPhaseTracker.getPhase();
    }

    /** Returns the number of consecutive AI frames spent in the current phase. */
    int getTeamPhaseFrames() {
        return teamPhaseTracker.getPhaseFrames();
    }

    /** Exposes the deterministic tracker to package-level regression scenarios. */
    TeamPhaseTracker getTeamPhaseTracker() {
        return teamPhaseTracker;
    }

    /** Returns passive development observations for the current tactical comparison window. */
    TacticalDebugMetrics getTacticalDebugMetrics() {
        return tacticalDebugMetrics;
    }

    /** Archives the current debug observations and starts a fresh tactical comparison window. */
    void startTacticalDebugComparisonWindow() {
        tacticalDebugMetrics.startComparisonWindow();
    }

    /** Records an executed pass for development telemetry without affecting match statistics. */
    void recordTacticalPass(Player passer, Player receiver, float ballAngle) {
        if (Settings.development && match != null) {
            tacticalDebugMetrics.recordPass(this, passer, receiver, ballAngle);
        }
    }

    /** Returns the active player wearing {@code shirtNumber}, or {@code null} if not fielded. */
    Player findPlayerByNumber(int shirtNumber) {
        if (lineup == null) return null;
        int activePlayers = Math.min(TEAM_SIZE, lineup.size());
        for (int i = 0; i < activePlayers; i++) {
            Player player = lineup.get(i);
            if (player.isActive && player.number == shirtNumber) return player;
        }
        return null;
    }

    /**
     * Returns the most central active attacker for the hold-up-play debug instruction.
     * ySoccer has no striker subtype, so horizontal formation position is the narrowest reliable
     * way to distinguish a centre-forward from wide attackers without changing squad data.
     */
    Player findPrimaryAttacker() {
        if (lineup == null) return null;
        Player primary = null;
        float nearestCentre = Float.MAX_VALUE;
        int activePlayers = Math.min(TEAM_SIZE, lineup.size());
        for (int i = 1; i < activePlayers; i++) {
            Player player = lineup.get(i);
            if (player.isActive && player.role == ATTACKER && Math.abs(player.tx) < nearestCentre) {
                primary = player;
                nearestCentre = Math.abs(player.tx);
            }
        }
        return primary;
    }

    public int nonAiInputDevicesCount() {
        int n = 0;
        for (Player player : players) {
            if (player.inputDevice != player.ai) n++;
        }
        return n;
    }

    public void deletePlayer(Player player) {
        if (players.size() > Const.BASE_TEAM) {
            players.remove(player);
        }
    }

    public Kit newKit() {
        if (kits.size() == MAX_KITS) {
            return null;
        }
        Kit kit = new Kit();
        kits.add(kit);
        return kit;
    }

    public Kit getKit() {
        return kits.get(kitIndex);
    }

    public boolean deleteKit() {
        if (kits.size() > MIN_KITS) {
            return kits.remove(kits.get(kits.size() - 1));
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != Team.class) return false;

        return Assets.teamsRootFolder.child(path).equals(Assets.teamsRootFolder.child(((Team) obj).path));
    }

    public void releaseNonAiInputDevices() {
        for (Player player : players) {
            if (player.inputDevice != player.ai) {
                player.inputDevice.available = true;
                player.inputDevice = player.ai;
            }
        }
    }

    boolean usesAutomaticInputDevice() {
        return (controlMode == ControlMode.PLAYER) && (inputDevice != null);
    }

    void setPlayersState(PlayerFsm.Id stateId, Player excluded) {
        for (int i = 0; i < TEAM_SIZE; i++) {
            Player player = lineup.get(i);
            if (player != excluded) {
                player.setState(stateId);
            }
        }
    }

    void setLineupState(PlayerFsm.Id stateId) {
        for (Player player : lineup) {
            player.setState(stateId);
        }
    }

    void setLineupTarget(float tx, float ty) {
        for (Player player : lineup) {
            player.setTarget(tx, ty);
        }
    }

    void assignAutomaticInputDevices(Player receiver) {
        manualPlayerSwitcher.reset();
        if (usesAutomaticInputDevice()) {
            for (int i = 0; i < TEAM_SIZE; i++) {
                Player player = lineup.get(i);
                if (player == receiver) {
                    player.inputDevice = player.team.inputDevice;
                } else {
                    player.inputDevice = player.ai;
                }
            }
        }
    }

    void automaticInputDeviceSelection() {
        Ball ball = match.ball;

        Player controlled = controlledPlayer();
        if (ball.owner != null && ball.owner.team == this) {
            // Possession is the only automatic override: action input must follow the player carrying the ball.
            if (ball.owner != controlled) {
                assignControlTo(ball.owner);
            }
        } else if (controlled == null) {
            // Establish control only when entering open play without any selected player.
            Player initialPlayer = initialControllablePlayer();
            if (initialPlayer != null) {
                assignControlTo(initialPlayer);
            }
        }
    }

    /**
     * Processes the dedicated switch button without allowing ordinary automatic selection.
     * The match state allows only one physics subframe to consume each rendered input frame.
     *
     * @param processButton whether this subframe may consume a new switch-button press
     */
    void updateManualPlayerSwitch(boolean processButton) {
        manualPlayerSwitcher.update();

        Ball ball = match.ball;
        if (ball.owner != null && ball.owner.team == this) {
            manualPlayerSwitcher.reset();
            return;
        }

        Player controlled = controlledPlayer();
        Player preferred = ball.ownerLast != null && ball.ownerLast.team != this
            ? bestDefender : null;

        if (processButton && inputDevice.fire3Down()) {
            Player selected = manualPlayerSwitcher.select(lineup, controlled, preferred);
            if (selected != null) {
                assignControlTo(selected);
            }
        }
    }

    /** Clears the button-cycle state when the match leaves normal open play. */
    void resetManualPlayerSwitch() {
        manualPlayerSwitcher.reset();
    }

    private Player controlledPlayer() {
        for (Player player : lineup) {
            if (player.inputDevice != player.ai) {
                return player;
            }
        }
        return null;
    }

    private void assignControlTo(Player selected) {
        for (Player player : lineup) {
            player.inputDevice = player == selected ? inputDevice : player.ai;
        }
    }

    private Player initialControllablePlayer() {
        if (near1 != null && near1.isActive && near1.role != GOALKEEPER) {
            return near1;
        }
        for (Player player : lineup) {
            if (player.isActive && player.role != GOALKEEPER) {
                return player;
            }
        }
        return null;
    }

    InputDevice fire1Down() {
        if (inputDevice != null) {
            if (inputDevice.fire1Down()) {
                return inputDevice;
            }
        } else {
            for (Player player : lineup) {
                if ((player.inputDevice != player.ai) && player.inputDevice.fire1Down()) {
                    return player.inputDevice;
                }
            }
        }
        return null;
    }

    InputDevice fire1Up() {
        if (inputDevice != null) {
            if (inputDevice.fire1Up()) {
                return inputDevice;
            }
        } else {
            for (Player player : lineup) {
                if ((player.inputDevice != player.ai) && player.inputDevice.fire1Up()) {
                    return player.inputDevice;
                }
            }
        }
        return null;
    }

    InputDevice fire2Down() {
        if (inputDevice != null) {
            if (inputDevice.fire2Down()) {
                return inputDevice;
            }
        } else {
            for (Player player : lineup) {
                if ((player.inputDevice != player.ai) && player.inputDevice.fire2Down()) {
                    return player.inputDevice;
                }
            }
        }
        return null;
    }

    public Player playerAtPosition(int pos) {
        if (pos < players.size()) {
            int ply = (pos < TEAM_SIZE) ? Tactics.order[Assets.tactics[tactics].basedOn][pos] : pos;
            return players.get(ply);
        } else {
            return null;
        }
    }

    Player lineupAtPosition(int pos) {
        if (pos < lineup.size()) {
            int ply = (pos < TEAM_SIZE) ? Tactics.order[Assets.tactics[tactics].basedOn][pos] : pos;
            return lineup.get(ply);
        } else {
            return null;
        }
    }

    Player lastOfLineup() {
        for (int pos = TEAM_SIZE - 1; pos > 0; pos--) {
            Player ply = lineupAtPosition(pos);
            if (ply.isActive) {
                return ply;
            }
        }
        return null;
    }

    public int playerIndexAtPosition(int pos) {
        return playerIndexAtPosition(pos, null);
    }

    public int playerIndexAtPosition(int pos, Tactics tcs) {
        if (tcs == null) {
            tcs = Assets.tactics[tactics];
        }
        if (pos < players.size()) {
            return (pos < TEAM_SIZE) ? Tactics.order[tcs.basedOn][pos] : pos;
        } else {
            return -1;
        }
    }

    int nearestBenchPlayerByRole(Player.Role role) {

        int level = -1;

        // in the first pass, search for the same role
        Player.Role target = role;

        while (true) {
            for (int pos = 0; pos < TEAM_SIZE; pos++) {
                if (lineupAtPosition(pos).role == target) {
                    return pos;
                }
            }

            level = level + 1;

            // no match found
            if (level == 2) {
                return 0;
            }

            target = substitutionRules.get(role)[level];
        }
    }

    int defenseRating() {
        int defense = 0;
        for (int p = 0; p < 11; p++) {
            defense += playerAtPosition(p).getDefenseRating();
        }
        return defense;
    }

    int offenseRating() {
        int offense = 0;
        for (int p = 0; p < 11; p++) {
            offense += playerAtPosition(p).getOffenseRating();
        }
        return offense;
    }

    public static void kitAutoSelection(Team homeTeam, Team awayTeam) {
        for (int i = 0; i < homeTeam.kits.size(); i++) {
            for (int j = 0; j < awayTeam.kits.size(); j++) {
                Kit homeKit = homeTeam.kits.get(i);
                Kit awayKit = awayTeam.kits.get(j);
                float difference = Kit.colorDifference(homeKit, awayKit);
                if (difference > 45) {
                    homeTeam.kitIndex = i;
                    awayTeam.kitIndex = j;
                    return;
                }
            }
        }
    }

    float getRank() {
        // absolute ranking from 0 to 10
        float r = 0;
        for (int i = 0; i < TEAM_SIZE; i++) {
            Player player = lineup.get(i);
            r += player.getValue() / 5.0f;
        }
        return r / TEAM_SIZE;
    }

    public void rotatePlayerNumber(Player player, int direction) {
        boolean used;
        do {
            player.number = EMath.rotate(player.number, 1, 99, direction);
            used = false;
            for (Player ply : players) {
                if (ply != player && ply.number == player.number) {
                    used = true;
                }
            }
        } while (used);
    }

    public void persist() {
        if (path == null || path.equals("")) {
            Gdx.app.error("Team", "Cannot save in empty path");
            return;
        }
        FileHandle fh = Assets.teamsRootFolder.child(path);
        String tmp = path;
        path = null;
        fh.writeString(Assets.json.prettyPrint(this), false, "UTF-8");
        path = tmp;
    }

    public static TeamList loadTeamList(List<String> paths) {
        TeamList teamList = new TeamList();
        for (String path : paths) {
            FileHandle teamFile = Assets.teamsRootFolder.child(path);
            if (teamFile.exists()) {
                Team team = Assets.json.fromJson(Team.class, teamFile.readString("UTF-8"));
                team.path = path;
                team.controlMode = Team.ControlMode.COMPUTER;
                teamList.add(team);
            }
        }
        return teamList;
    }

    public void loadImage() {
        if (image != null) return;

        switch (type) {
            case CLUB:
            case CUSTOM:
                String logoPath = path.replaceFirst("/team.", "/logo.").replaceFirst(".json", ".png");
                FileHandle customLogo = Assets.teamsRootFolder.child(logoPath);
                if (customLogo.exists()) {
                    Texture texture = new Texture(customLogo);
                    if (!Gui.zoomIsInteger()) {
                        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                    }
                    image = new TextureRegion(texture);
                    image.flip(false, true);
                } else {
                    image = kits.get(0).loadLogo();
                    imageIsDefaultLogo = true;
                }
                break;

            case NATIONAL:
                // custom flag
                String flagPath = path.replaceFirst("/team.", "/flag.").replaceFirst(".json", ".png");
                FileHandle file = Assets.teamsRootFolder.child(flagPath);
                if (!file.exists()) {
                    // default flag
                    file = Gdx.files.internal("images/flags/medium/" + name.toLowerCase().replace(" ", "_").replace(".", "") + ".png");
                }
                if (file.exists()) {
                    Texture texture = new Texture(file);
                    if (!Gui.zoomIsInteger()) {
                        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                    }
                    image = new TextureRegion(texture);
                    image.flip(false, true);
                }
                break;
        }
    }

    public TextureRegion loadKit(int index) {
        // custom kit - all indexes
        String kitPath = path.replaceFirst("/team.", "/kit.").replaceFirst(".json", ".png");
        FileHandle file = Assets.teamsRootFolder.child(kitPath);
        if (file.exists()) {
            List<RgbPair> rgbPairs = new ArrayList<>();
            kits.get(index).addKitColors(rgbPairs);
            return Assets.loadTextureRegion(file.path(), rgbPairs);
        }

        // custom kit - single index
        String[] names = {"home", "away", "third", "change1", "change2"};
        kitPath = path.replaceFirst("/team.", "/kit_" + names[index] + ".").replaceFirst(".json", ".png");
        file = Assets.teamsRootFolder.child(kitPath);
        if (file.exists()) {
            List<RgbPair> rgbPairs = new ArrayList<>();
            kits.get(index).addKitColors(rgbPairs);
            return Assets.loadTextureRegion(file.path(), rgbPairs);
        }

        // standard kit
        return kits.get(index).loadImage();
    }

    public TextureRegion loadMiniKit(int index) {
        return kits.get(index).loadMiniImage();
    }

    public int controlModeColor() {
        return controlModeColors[controlMode.ordinal()];
    }

    Player searchPlayerTackledBy(Player tackler) {
        for (int i = 0; i < TEAM_SIZE; i++) {
            Player ply = lineup.get(i);
            float feetDistance = EMath.dist(ply.x, ply.y, tackler.x, tackler.y);
            float bodyDistance = EMath.dist(ply.x, ply.y, tackler.x - 9 * EMath.cos(tackler.a), tackler.y - 9 * EMath.sin(tackler.a));
            if (feetDistance < 7 || bodyDistance < 9) {
                return ply;
            }
        }
        return null;
    }

    public boolean hasUnavailablePlayers() {
        for (int i = 0; i < TEAM_SIZE; i++) {
            Player player = players.get(i);
            if (player.suspensions > 0) return true;
        }
        return false;
    }
}
