package com.ygames.ysoccer.match;

import com.badlogic.gdx.Gdx;
import com.ygames.ysoccer.framework.Settings;
import com.ygames.ysoccer.framework.SoundManager;

import static com.badlogic.gdx.Input.Keys.CONTROL_LEFT;
import static com.badlogic.gdx.Input.Keys.CONTROL_RIGHT;
import static com.badlogic.gdx.Input.Keys.F10;
import static com.badlogic.gdx.Input.Keys.F4;
import static com.badlogic.gdx.Input.Keys.F5;
import static com.badlogic.gdx.Input.Keys.F9;
import static com.badlogic.gdx.Input.Keys.NUM_1;
import static com.badlogic.gdx.Input.Keys.NUM_2;
import static com.badlogic.gdx.Input.Keys.NUM_3;
import static com.badlogic.gdx.Input.Keys.NUM_4;
import static com.badlogic.gdx.Input.Keys.NUM_5;
import static com.badlogic.gdx.Input.Keys.NUM_6;
import static com.badlogic.gdx.Input.Keys.P;
import static com.badlogic.gdx.Input.Keys.SPACE;
import static com.ygames.ysoccer.framework.Assets.gettext;
import static com.ygames.ysoccer.framework.InputDevice.keyDescription;

public class MatchHotKeys extends SceneHotKeys {

    private static final int[] TACTICAL_KEYS = {NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6};

    private boolean keyCommentary;
    private boolean keyCrowdChants;
    private boolean keyAutoReplay;
    private boolean keyRadar;
    private boolean keyRecordAction;
    private final boolean[] tacticalKeys = new boolean[6];

    public MatchHotKeys(Match match, MatchRenderer matchRenderer) {
        super(match, matchRenderer);

        String[] matchCommentary = {keyDescription(F4), gettext("HELP.MATCH COMMENTARY")};
        keyMap.put(4, matchCommentary);

        String[] crowdChants = {keyDescription(F5), gettext("HELP.CROWD CHANTS")};
        keyMap.put(5, crowdChants);

        String[] autoReplay = {keyDescription(F9), gettext("HELP.AUTO REPLAYS")};
        keyMap.put(10, autoReplay);

        String[] radar = {keyDescription(F10), gettext("HELP.RADAR")};
        keyMap.put(11, radar);

        String[] recordAction = {keyDescription(SPACE), gettext("HELP.RECORD ACTION")};
        keyMap.put(13, recordAction);

        String[] pause = {keyDescription(P), gettext("HELP.PAUSE")};
        keyMap.put(14, pause);

        if (Settings.development) {
            keyMap.put(17, new String[] {"CTRL+1-6", "TACTICAL DEBUG PRESETS"});
        }
    }

    private Match getMatch() {
        return (Match) scene;
    }

    @Override
    public void update() {
        super.update();

        if (Gdx.input.isKeyPressed(F4) && !keyCommentary) {
            scene.settings.commentary = !scene.settings.commentary;

            message = gettext("MATCH OPTIONS.COMMENTARY") + " ";
            if (scene.settings.commentary) {
                message += gettext("MATCH OPTIONS.COMMENTARY.ON");
            } else {
                message += gettext("MATCH OPTIONS.COMMENTARY.OFF");
            }
            messageTimer = 60;
        }

        if (Gdx.input.isKeyPressed(F5) && !keyCrowdChants) {
            SoundManager.crowdChantsEnabled = !SoundManager.crowdChantsEnabled;
            SoundManager.setIntroVolume();
            SoundManager.setCrowdVolume();

            message = gettext("MATCH OPTIONS.CROWD CHANTS") + " ";
            if (SoundManager.crowdChantsEnabled) {
                message += gettext("MATCH OPTIONS.CROWD CHANTS.ON");
            } else {
                message += gettext("MATCH OPTIONS.CROWD CHANTS.OFF");
            }
            messageTimer = 60;
        }

        if (Gdx.input.isKeyPressed(F9) && !keyAutoReplay) {
            getMatch().getSettings().autoReplays = !getMatch().getSettings().autoReplays;

            message = gettext("AUTO REPLAYS") + " ";

            if (getMatch().getSettings().autoReplays) {
                message += gettext("AUTO REPLAYS.ON");
            } else {
                message += gettext("AUTO REPLAYS.OFF");
            }

            messageTimer = 60;
        }

        if (Gdx.input.isKeyPressed(F10) && !keyRadar) {
            getMatch().getSettings().radar = !getMatch().getSettings().radar;

            message = gettext("RADAR") + " ";
            if (getMatch().getSettings().radar) {
                message += gettext("RADAR.ON");
            } else {
                message += gettext("RADAR.OFF");
            }

            messageTimer = 60;
        }

        if (Gdx.input.isKeyPressed(SPACE) && !keyRecordAction) {
            getMatch().recorder.saveHighlight();

            message = gettext("ACTION RECORDED");
            messageTimer = 60;
        }

        updateTacticalDebugKeys();

        keyCommentary = Gdx.input.isKeyPressed(F4);
        keyCrowdChants = Gdx.input.isKeyPressed(F5);
        keyAutoReplay = Gdx.input.isKeyPressed(F9);
        keyRadar = Gdx.input.isKeyPressed(F10);
        keyRecordAction = Gdx.input.isKeyPressed(SPACE);
    }

    /** Applies non-conflicting development-only tactical presets to the locally relevant team. */
    private void updateTacticalDebugKeys() {
        boolean control = Gdx.input.isKeyPressed(CONTROL_LEFT)
            || Gdx.input.isKeyPressed(CONTROL_RIGHT);
        for (int i = 0; i < TACTICAL_KEYS.length; i++) {
            boolean pressed = Settings.development && control
                && Gdx.input.isKeyPressed(TACTICAL_KEYS[i]);
            if (pressed && !tacticalKeys[i]) applyTacticalPreset(i + 1);
            tacticalKeys[i] = pressed;
        }
    }

    /** Maps the six demo commands to coach intent without directly changing player coordinates. */
    private void applyTacticalPreset(int preset) {
        Team team = getMatch().tacticalDebugTeam();
        if (team == null || team.lineup == null) return;

        TacticalState state = team.getTacticalState();
        switch (preset) {
            case 1:
                state.setShape(0.72f, 1.35f);
                message = "TACTIC: COMPACT SHAPE";
                break;

            case 2:
                state.resetShape();
                message = "TACTIC: DEFAULT SHAPE";
                break;

            case 3:
                Player forward = team.findPrimaryAttacker();
                if (forward == null) {
                    message = "TACTIC: NO ACTIVE CENTRE FORWARD";
                } else {
                    state.addPlayerInstruction(forward, PlayerTacticalInstruction.holdUpForward());
                    message = "TACTIC: #" + forward.number + " HOLD UP";
                }
                break;

            case 4:
                Player playerSeven = team.findPlayerByNumber(7);
                if (playerSeven == null) {
                    message = "TACTIC: NO ACTIVE #7";
                } else {
                    state.addPlayerInstruction(playerSeven, PlayerTacticalInstruction.defendMore());
                    message = "TACTIC: #7 DEFEND MORE";
                }
                break;

            case 5:
                state.setPossessionProfile(0.65f, 0.55f, 0.55f, 1.45f, 1.60f);
                message = "TACTIC: SLOW TEMPO";
                break;

            case 6:
                state.resetAll();
                message = "TACTIC: DEFAULT STATE";
                break;

            default:
                return;
        }
        team.startTacticalDebugComparisonWindow();
        Settings.showDevelopmentInfo = true;
        messageTimer = 180;
    }

    @Override
    void onChangeVolume() {
        super.onChangeVolume();

        SoundManager.setIntroVolume();
        SoundManager.setCrowdVolume();
    }
}
