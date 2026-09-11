package com.ygames.ysoccer.framework;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.utils.Json;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;

/** Hardware-free regression checks for local Xbox input, legacy settings and player-slot reconnection. */
public final class ControllerRegressionTest {
    private static int checks;

    /** Runs without a graphics context or additional test dependencies; failures terminate with a nonzero exit. */
    public static void main(String[] args) {
        defaultsAndLegacySettings();
        stickAndDpad();
        actionEdgesAndDisconnect();
        reconnectionAndAvailability();
        disconnectedSettingsSurvive();
        System.out.println("Controller regression checks passed: " + checks);
    }

    private static void defaultsAndLegacySettings() {
        Pad pad = new Pad("one");
        JoystickConfig config = JoystickConfig.forController(pad.controller);
        check(config.xAxis == 0 && config.yAxis == 1, "standard left stick");
        check(config.button1 == 0 && config.button2 == 1 && config.button3 == 2, "Xbox A/B/X actions");
        config.bindButton(1, 1);
        check(config.button1 == 1 && config.button2 == 0, "A/B bindings can be swapped");
        config.bindButton(3, 1);
        check(config.button3 == 1 && config.button1 == 2, "switch binding can exchange an occupied action");
        config.reset(pad.controller);
        check(config.button1 == 0 && config.button2 == 1 && config.button3 == 2, "reset restores standard actions");
        Json json = new Json();
        JoystickConfig legacy = json.fromJson(JoystickConfig.class,
            "{name:Xbox,xAxis:0,yAxis:1,button1:3,button2:2}");
        check(legacy.button1 == 3 && legacy.button2 == 2 && legacy.button3 == -1, "legacy custom actions preserved");
        check(legacy.deadZone == 0.3f && !legacy.invertX && !legacy.invertY, "legacy calibration defaults");
        legacy.deadZone = 0.45f;
        legacy.invertY = true;
        JoystickConfig copy = json.fromJson(JoystickConfig.class, json.toJson(legacy));
        check(copy.deadZone == 0.45f && copy.invertY, "calibration round trip");
        pad.axisCount = 0;
        pad.maxButton = -1;
        JoystickConfig unsupported = JoystickConfig.forController(pad.controller);
        check(unsupported.xAxis == -1 && unsupported.button1 == -1, "unsupported mapping remains unbound");
        Joystick joystick = new Joystick(pad.controller, unsupported, 0);
        joystick.update();
        check(!joystick.fire10 && joystick.x0 == 0, "unbound controls never queried");
        pad.mapping = null;
        check(!JoystickConfig.forController(pad.controller).isConfigured(), "missing backend mapping");
    }

    private static void stickAndDpad() {
        Pad pad = new Pad("one");
        JoystickConfig config = JoystickConfig.forController(pad.controller);
        Joystick joystick = new Joystick(pad.controller, config, 0);
        joystick.update();
        pad.axes[0] = 0.18f;
        joystick.update();
        check(joystick.x0 == 0, "resting drift ignored");
        pad.axes[0] = 0.31f;
        pad.axes[1] = -0.31f;
        joystick.update();
        check(joystick.x0 == 1 && joystick.y0 == -1, "low-deflection diagonal engages symmetrically");
        pad.axes[0] = 0.25f;
        joystick.update();
        check(joystick.x0 == 1, "hysteresis holds near threshold");
        pad.axes[0] = 0.18f;
        joystick.update();
        check(joystick.x0 == 0, "stick returns to neutral");
        pad.axes[0] = -0.5f;
        joystick.update();
        check(joystick.x0 == -1, "negative threshold is symmetric");
        pad.axes[0] = 0.5f;
        joystick.update();
        check(joystick.x0 == 1, "direct reversal engages immediately");
        pad.buttons[13] = true;
        joystick.update();
        check(joystick.x0 == -1 && joystick.y0 == 0, "D-pad overrides both stick axes");
        pad.buttons[11] = true;
        joystick.update();
        check(joystick.x0 == -1 && joystick.y0 == -1, "D-pad diagonals");
        pad.buttons[14] = true;
        joystick.update();
        check(joystick.x0 == 0, "opposing D-pad buttons cancel");
        Arrays.fill(pad.buttons, false);
        config.invertX = config.invertY = true;
        joystick.update();
        check(joystick.x0 == -1 && joystick.y0 == 1, "manual polarity correction");
        config.xAxis = 99;
        config.button1 = 99;
        config.deadZone = Float.NaN;
        joystick.update();
        check(joystick.x0 == 0 && !joystick.fire10, "invalid saved indices are safe");

        // Bench navigation relies on the old buffered axis being present on the release frame.
        config.invertY = false;
        pad.axes[1] = 0;
        joystick.update();
        joystick.update();
        pad.axes[1] = 1;
        joystick.update();
        joystick.update();
        pad.axes[1] = 0;
        joystick.update();
        check(joystick.yMoved() && joystick.y1 == 1, "bench release preserves navigation direction");
    }

    private static void actionEdgesAndDisconnect() {
        Pad pad = new Pad("one");
        JoystickConfig config = JoystickConfig.forController(pad.controller);
        Joystick joystick = new Joystick(pad.controller, config, 0);
        pad.buttons[0] = true;
        joystick.update();
        check(!joystick.fire1Down(), "held confirmation suppressed on connection/rebind");
        pad.buttons[0] = false;
        joystick.update();
        pad.buttons[0] = true;
        pad.buttons[2] = true;
        joystick.update();
        check(joystick.fire1Down() && joystick.fire3Down(), "independent kick and switch edges");
        joystick.update();
        check(!joystick.fire1Down() && !joystick.fire3Down(), "held buttons do not retrigger");
        pad.buttons[0] = false;
        joystick.update();
        check(joystick.fire1Up(), "kick release preserved");
        pad.buttons[7] = true;
        joystick.update();
        check(joystick.pauseDown(), "Start pauses on press");
        joystick.update();
        check(!joystick.pauseDown(), "holding Start does not toggle repeatedly");
        pad.buttons[7] = false;
        joystick.update();
        pad.buttons[7] = true;
        joystick.update();
        check(joystick.pauseDown(), "second Start press can resume");
        config.button2 = 7;
        joystick.update();
        check(!joystick.pauseDown() && joystick.fire20, "custom Start binding keeps gameplay priority");
        pad.axes[0] = 1;
        pad.buttons[0] = true;
        joystick.update();
        joystick.update();
        pad.connected = false;
        joystick.update();
        check(!joystick.value && joystick.x0 == 0 && joystick.x1 == 0, "disconnect immediately clears movement");
        check(!joystick.fire10 && !joystick.fire11 && !joystick.fire1Up(), "disconnect does not synthesize a kick");
        check(!joystick.pauseDown(), "disconnect clears pause");
    }

    private static void reconnectionAndAvailability() {
        Pad first = new Pad("first");
        Pad second = new Pad("second");
        InputDeviceList devices = new InputDeviceList();
        devices.connectController(first.controller, JoystickConfig.forController(first.controller));
        devices.connectController(second.controller, JoystickConfig.forController(second.controller));
        check(devices.size() == 2 && devices.get(0).port != devices.get(1).port, "same-model controllers get separate slots");
        InputDevice player = devices.assignFirstAvailable();
        check(player == devices.get(0), "first controller assigned");
        first.connected = false;
        Pad reconnect = new Pad("first");
        devices.connectController(reconnect.controller, JoystickConfig.forController(reconnect.controller));
        check(devices.size() == 2 && devices.get(0) == player && player.isConnected(), "reconnect retains player reference");
        check(!player.available, "reconnect retains assignment");
        devices.connectController(reconnect.controller, JoystickConfig.forController(reconnect.controller));
        check(devices.size() == 2, "duplicate connection ignored");
        check(devices.findController(reconnect.controller) == player, "preview uses the assigned gameplay input");
        second.connected = false;
        check(devices.getAvailabilityCount() == 0 && devices.assignFirstAvailable() == null, "disconnected slots not offered");
        Pad changedId = new Pad("new-backend-id");
        devices.connectController(changedId.controller, JoystickConfig.forController(changedId.controller));
        check(devices.size() == 2 && devices.get(1).isConnected(), "changed backend ID uses free same-model slot");
        reconnect.connected = false;
        changedId.connected = false;
        check(devices.rotateAvailable(player, 1) == player, "all-disconnected rotation is bounded");
    }

    private static void disconnectedSettingsSurvive() {
        // Settings only needs default-valued preferences for this persistence regression.
        Preferences preferences = (Preferences) Proxy.newProxyInstance(Preferences.class.getClassLoader(),
            new Class<?>[]{Preferences.class}, (proxy, method, args) -> args != null && args.length == 2 ? args[1] : null);
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
            new Class<?>[]{Application.class}, (proxy, method, args) ->
                method.getName().equals("getPreferences") ? preferences : null);
        Settings settings = new Settings();
        JoystickConfig absent = new JoystickConfig("Unplugged controller");
        absent.button1 = 8;
        settings.setJoystickConfigs(new ArrayList<>(Arrays.asList(absent)));
        JoystickConfig edited = new JoystickConfig("Xbox");
        edited.xAxis = 1;
        settings.setJoystickConfigs(new ArrayList<>(Arrays.asList(edited)));
        check(settings.getJoystickConfigByName(absent.name).button1 == 8, "saving connected devices retains unplugged settings");
        check(settings.getJoystickConfigByName("Xbox").xAxis == 1, "partial setup is retained");
        Gdx.app = null;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    /** Minimal Xbox-shaped backend that rejects out-of-range reads, including unplugged access. */
    private static final class Pad {
        final float[] axes = new float[6];
        final boolean[] buttons = new boolean[15];
        boolean connected = true;
        int axisCount = 6;
        int maxButton = 14;
        ControllerMapping mapping = new XboxMapping();
        final Controller controller;

        Pad(String id) {
            controller = (Controller) Proxy.newProxyInstance(Controller.class.getClassLoader(),
                new Class<?>[]{Controller.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName": return connected ? "Xbox" : "Unknown";
                        case "getUniqueId": return id;
                        case "isConnected": return connected;
                        case "getAxisCount": return axisCount;
                        case "getMinButtonIndex": return 0;
                        case "getMaxButtonIndex": return maxButton;
                        case "getMapping": return mapping;
                        case "getAxis":
                            int axis = (Integer) args[0];
                            if (!connected || axis < 0 || axis >= axisCount) throw new AssertionError("invalid axis query");
                            return axes[axis];
                        case "getButton":
                            int button = (Integer) args[0];
                            if (!connected || button < 0 || button > maxButton) throw new AssertionError("invalid button query");
                            return buttons[button];
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
        }
    }

    private static final class XboxMapping extends ControllerMapping {
        XboxMapping() {
            super(0, 1, 2, 3, 0, 1, 2, 3, 6, 7, 4, -1, 5, -1, 9, 10, 11, 12, 13, 14);
        }
    }
}
