package com.ygames.ysoccer.framework;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;

/**
 * A stable local player slot backed by a replaceable physical controller.
 * Converts analog input to the game's eight directions while preserving buffered bench-navigation edges.
 */
class Joystick extends InputDevice {

    private Controller controller;
    /** Cached while attached because this backend returns "Unknown" after unplugging. */
    private String controllerName;
    private JoystickConfig config;
    private int stickX, stickY;
    private boolean pause, pauseOld;
    /** Require released controls after connection/rebinding to avoid accidental menu activation or kicks. */
    private boolean awaitingNeutral;

    Joystick(Controller controller, JoystickConfig config, int port) {
        super(Type.JOYSTICK, port);
        reconnect(controller, config);
    }

    /** Reattaches hardware without replacing references held by a team or individual player. */
    void reconnect(Controller controller, JoystickConfig config) {
        this.controller = controller;
        this.controllerName = controller.getName();
        this.config = config;
        clearState();
    }

    boolean usesController(Controller candidate) {
        return controller == candidate;
    }

    boolean hasSameId(Controller candidate) {
        String id = controller.getUniqueId();
        return id != null && !id.isEmpty() && id.equals(candidate.getUniqueId());
    }

    boolean hasSameModel(Controller candidate) {
        return controllerName.equals(candidate.getName());
    }

    @Override
    public boolean isConnected() {
        return controller.isConnected();
    }

    /** Samples once per frame, clearing all buffered input immediately when the hardware disconnects. */
    @Override
    public void update() {
        if (!isConnected()) {
            // Do not synthesize a kick-release or retain movement when a cable is removed.
            clearState();
            return;
        }
        super.update();
    }

    @Override
    protected void read() {
        float deadZone = Float.isNaN(config.deadZone) ? 0.3f
            : Math.max(0.1f, Math.min(0.8f, config.deadZone));
        stickX = direction(axis(config.xAxis) * (config.invertX ? -1 : 1), stickX, deadZone);
        stickY = direction(axis(config.yAxis) * (config.invertY ? -1 : 1), stickY, deadZone);
        x0 = stickX;
        y0 = stickY;
        ControllerMapping mapping = controller.getMapping();
        if (mapping != null) {
            boolean left = button(mapping.buttonDpadLeft);
            boolean right = button(mapping.buttonDpadRight);
            boolean up = button(mapping.buttonDpadUp);
            boolean down = button(mapping.buttonDpadDown);
            // Intentional D-pad input takes priority over the stick, including its resting drift.
            if (left || right || up || down) {
                x0 = (right ? 1 : 0) - (left ? 1 : 0);
                y0 = (down ? 1 : 0) - (up ? 1 : 0);
            }
        }
        fire10 = button(config.button1);
        fire20 = button(config.button2);
        fire30 = button(config.button3);
        pauseOld = pause;
        pause = mapping != null && mapping.buttonStart != config.button1
            && mapping.buttonStart != config.button2 && mapping.buttonStart != config.button3
            && button(mapping.buttonStart);
        if (awaitingNeutral) {
            awaitingNeutral = x0 != 0 || y0 != 0 || fire10 || fire20 || fire30 || pause;
            x0 = y0 = 0;
            fire10 = fire20 = fire30 = pause = false;
        }
    }

    /** Start is edge-triggered so holding it cannot repeatedly pause/resume a match. */
    @Override
    public boolean pauseDown() {
        return pause && !pauseOld;
    }

    private float axis(int index) {
        return index >= 0 && index < controller.getAxisCount() ? controller.getAxis(index) : 0;
    }

    private boolean button(int index) {
        return index >= 0 && index >= controller.getMinButtonIndex()
            && index <= controller.getMaxButtonIndex() && controller.getButton(index);
    }

    /** Separate engage/release thresholds prevent chatter near the stick's dead zone. */
    private static int direction(float value, int previous, float deadZone) {
        if (value >= deadZone) return 1;
        if (value <= -deadZone) return -1;
        float release = deadZone * 0.65f;
        if (previous > 0 && value > release) return 1;
        if (previous < 0 && value < -release) return -1;
        return 0;
    }

    private void clearState() {
        stickX = stickY = x0 = y0 = x1 = y1 = angle = 0;
        fire10 = fire20 = fire30 = fire11 = fire21 = fire31 = value = false;
        pause = pauseOld = false;
        awaitingNeutral = true;
    }
}
