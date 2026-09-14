package com.ygames.ysoccer.framework;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Json;
import com.ygames.ysoccer.match.MatchViewMode;
import com.ygames.ysoccer.match.Weather;

import java.util.ArrayList;
import java.util.Arrays;

public class Settings {

    private Preferences preferences;
    private Json json;

    public final String APP_NAME = "YSoccer";
    public final String VERSION = "25";

    // game
    public String locale;
    public boolean fullScreen;
    public boolean showIntro;
    public int musicMode;
    public int musicVolume;

    // match
    public static Integer[] matchLengths = {3, 5, 7, 10};
    public int matchLength;
    public int benchSize;
    public boolean useFlags;
    public double playerPriceFactor;
    public String currency;
    public int weatherMaxStrength;
    public int zoom;
    /** Local presentation used when a new offline match or training session is created. */
    public MatchViewMode matchViewMode;
    public boolean radar;
    public boolean autoReplays;
    public int soundVolume;
    public boolean commentary;

    // controls
    private String keyboardConfigs;
    private String joystickConfigs;

    // network
    public static String serverAddress;
    public static int tcpPort;
    public static int udpPort;

    // development
    public static boolean development;
    public static String serverHomeTeam;
    public static String serverAwayTeam;

    // (logs)
    public static int logLevel;
    static int logFilter;

    public static boolean showJavaHeap;
    public static boolean showTeamValues;

    public static boolean showDevelopmentInfo;
    public static boolean showBallZones;
    public static boolean showBallPredictions;
    public static boolean showPlayerNumber;
    public static boolean showBestDefender;
    public static boolean showFrameDistance;
    public static boolean showPlayerState;
    public static boolean showPlayerAiState;

    public Settings() {
        preferences = Gdx.app.getPreferences(APP_NAME + VERSION);

        json = new Json();
        json.addClassTag("KeyboardConfig", KeyboardConfig.class);
        json.addClassTag("JoystickConfig", JoystickConfig.class);

        // game
        locale = preferences.getString("locale", "en");
        fullScreen = preferences.getBoolean("fullScreen", false);
        showIntro = preferences.getBoolean("showIntro", true);
        musicMode = preferences.getInteger("musicMode", MenuMusic.ALL);
        musicVolume = preferences.getInteger("musicVolume", 40);
        useFlags = preferences.getBoolean("useFlags", true);
        playerPriceFactor = preferences.getInteger("playerPriceFactorM", 1)
            * Math.pow(10, preferences.getInteger("playerPriceFactorE", 3));
        currency = preferences.getString("currency", "€");

        // match
        matchLength = preferences.getInteger("matchLength", matchLengths[0]);
        benchSize = preferences.getInteger("benchSize", 5);
        weatherMaxStrength = preferences.getInteger("weatherMaxStrength", Weather.Strength.LIGHT);
        zoom = preferences.getInteger("zoom", 100);
        matchViewMode = MatchViewMode.fromPreference(preferences.getString("matchViewMode", null));
        radar = preferences.getBoolean("radar", true);
        autoReplays = preferences.getBoolean("autoReplays", true);
        soundVolume = preferences.getInteger("soundVolume", 40);
        commentary = preferences.getBoolean("commentary", true);

        // controls
        keyboardConfigs = preferences.getString("keyboardConfigs", defaultKeyboardConfigs());
        joystickConfigs = preferences.getString("joystickConfigs", "[]");

        // (network)
        serverAddress = preferences.getString("serverAddress", "127.0.0.1");
        tcpPort = preferences.getInteger("tcpPort", 54555);
        udpPort = preferences.getInteger("udpPort", 54777);

        // development
        development = preferences.getBoolean("development", false);
        serverHomeTeam = preferences.getString("serverHomeTeam", "1964-65/CLUB_TEAMS/EUROPE/ITALY/team.inter_milan.json");
        serverAwayTeam = preferences.getString("serverAwayTeam", "1964-65/CLUB_TEAMS/EUROPE/ITALY/team.juventus.json");

        // (logs)
        logLevel = preferences.getInteger("logLevel", Application.LOG_INFO);
        logFilter = preferences.getInteger("logFilter", 0);

        // (gui)
        showJavaHeap = preferences.getBoolean("showJavaHeap", false);
        showTeamValues = preferences.getBoolean("showTeamValues", false);

        // (match)
        showBallZones = preferences.getBoolean("showBallZones", false);
        showBallPredictions = preferences.getBoolean("showBallPredictions", false);
        showPlayerNumber = preferences.getBoolean("showPlayerNumber", false);
        showBestDefender = preferences.getBoolean("showBestDefender", false);
        showFrameDistance = preferences.getBoolean("showFrameDistance", false);
        showPlayerState = preferences.getBoolean("showPlayerState", false);
        showPlayerAiState = preferences.getBoolean("showPlayerAiState", false);
    }

    public void save() {
        // game
        preferences.putString("locale", locale);
        preferences.putBoolean("fullScreen", fullScreen);
        preferences.putBoolean("showIntro", showIntro);
        preferences.putInteger("musicMode", musicMode);
        preferences.putInteger("musicVolume", musicVolume);
        preferences.putBoolean("useFlags", useFlags);
        int e = (int) Math.log10(playerPriceFactor);
        int m = (int) (playerPriceFactor / Math.pow(10, e));
        preferences.putInteger("playerPriceFactorM", m);
        preferences.putInteger("playerPriceFactorE", e);
        preferences.putString("currency", currency);

        // match
        preferences.putInteger("matchLength", matchLength);
        preferences.putInteger("benchSize", benchSize);
        preferences.putInteger("weatherMaxStrength", weatherMaxStrength);
        preferences.putInteger("zoom", zoom);
        preferences.putString("matchViewMode", matchViewMode.name());
        preferences.putBoolean("radar", radar);
        preferences.putBoolean("autoReplays", autoReplays);
        preferences.putInteger("soundVolume", soundVolume);
        preferences.putBoolean("commentary", commentary);

        // controls
        preferences.putString("keyboardConfigs", keyboardConfigs);
        preferences.putString("joystickConfigs", joystickConfigs);

        // network
        preferences.putString("serverAddress", serverAddress);
        preferences.putInteger("tcpPort", tcpPort);
        preferences.putInteger("udpPort", udpPort);

        // development
        preferences.putBoolean("development", development);
        preferences.putString("serverHomeTeam", serverHomeTeam);
        preferences.putString("serverAwayTeam", serverAwayTeam);

        // (logs)
        preferences.putInteger("logLevel", logLevel);
        preferences.putInteger("logFilter", logFilter);

        // (gui)
        preferences.putBoolean("showJavaHeap", showJavaHeap);
        preferences.putBoolean("showTeamValues", showTeamValues);

        // (match)
        preferences.putBoolean("showBallZones", showBallZones);
        preferences.putBoolean("showBallPredictions", showBallPredictions);
        preferences.putBoolean("showPlayerNumber", showPlayerNumber);
        preferences.putBoolean("showBestDefender", showBestDefender);
        preferences.putBoolean("showFrameDistance", showFrameDistance);
        preferences.putBoolean("showPlayerState", showPlayerState);
        preferences.putBoolean("showPlayerAiState", showPlayerAiState);

        preferences.flush();
    }

    private String defaultKeyboardConfigs() {
        ArrayList<KeyboardConfig> keyboardConfigs = new ArrayList<>();
        keyboardConfigs.add(new KeyboardConfig(Input.Keys.LEFT, Input.Keys.RIGHT, Input.Keys.UP,
            Input.Keys.DOWN, Input.Keys.M, Input.Keys.N, Input.Keys.COMMA));
        keyboardConfigs.add(new KeyboardConfig(Input.Keys.A, Input.Keys.D, Input.Keys.W,
            Input.Keys.S, Input.Keys.V, Input.Keys.B, Input.Keys.C));
        return json.toJson(keyboardConfigs);
    }

    public ArrayList<KeyboardConfig> getKeyboardConfigs() {
        ArrayList<KeyboardConfig> configs = new ArrayList<>(Arrays.asList(
            json.fromJson(KeyboardConfig[].class, keyboardConfigs)
        ));
        int[] preferredSwitchKeys = {Input.Keys.COMMA, Input.Keys.C};
        for (int port = 0; port < configs.size() && port < preferredSwitchKeys.length; port++) {
            KeyboardConfig config = configs.get(port);
            int preferredKey = preferredSwitchKeys[port];
            if (config.button3 < 0 && !isAssigned(configs, preferredKey)) {
                config.button3 = preferredKey;
            }
        }
        return configs;
    }

    private boolean isAssigned(ArrayList<KeyboardConfig> configs, int keyCode) {
        for (KeyboardConfig config : configs) {
            if (config.keyLeft == keyCode || config.keyRight == keyCode
                || config.keyUp == keyCode || config.keyDown == keyCode
                || config.button1 == keyCode || config.button2 == keyCode
                || config.button3 == keyCode) {
                return true;
            }
        }
        return false;
    }

    public void setKeyboardConfigs(ArrayList<KeyboardConfig> keyboardConfigs) {
        this.keyboardConfigs = json.toJson(keyboardConfigs);
    }

    private ArrayList<JoystickConfig> getJoystickConfigs() {
        return new ArrayList<>(Arrays.asList(json.fromJson(JoystickConfig[].class, joystickConfigs)));
    }

    public JoystickConfig getJoystickConfigByName(String name) {
        for (JoystickConfig joystickConfig : getJoystickConfigs()) {
            if (joystickConfig.name.equals(name)) {
                return joystickConfig;
            }
        }
        return null;
    }

    /** Saves edited models while retaining bindings for controllers that are currently unplugged. */
    public void setJoystickConfigs(ArrayList<JoystickConfig> joystickConfigs) {
        ArrayList<JoystickConfig> saved = getJoystickConfigs();
        for (JoystickConfig edited : joystickConfigs) {
            saved.removeIf(config -> edited.name.equals(config.name));
            saved.add(edited);
        }
        this.joystickConfigs = json.toJson(saved);
    }
}
