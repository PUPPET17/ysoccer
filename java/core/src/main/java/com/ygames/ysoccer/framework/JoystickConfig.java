package com.ygames.ysoccer.framework;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;

/** Saved gameplay bindings for a controller model; absent fields retain legacy-compatible defaults. */
public class JoystickConfig extends InputDeviceConfig {

    /** Backend model name used to share bindings, never to identify a particular player's device. */
    public String name;
    /** Horizontal stick axis, or -1 when manual configuration is needed. */
    public int xAxis = -1;
    /** Vertical stick axis; gameplay coordinates increase downwards. */
    public int yAxis = -1;
    /** Primary kick/pass action and menu confirmation. */
    public int button1 = -1;
    /** Secondary gameplay action and the menu's alternate/decrease action. */
    public int button2 = -1;
    /** Optional player-switch button; old saved controller configurations leave it unset. */
    public int button3 = -1;
    /** Stick deflection required to engage movement, in normalized axis units (0.1 to 0.8). */
    public float deadZone = 0.3f;
    /** Corrects a horizontal axis whose physical polarity is reversed. */
    public boolean invertX;
    /** Corrects a vertical axis whose values increase upwards. */
    public boolean invertY;

    public JoystickConfig() {
        super(InputDevice.Type.JOYSTICK);
    }

    public JoystickConfig(String name) {
        super(InputDevice.Type.JOYSTICK);
        this.name = name;
    }

    /**
     * Creates first-use bindings from the backend mapping; callers must prefer saved custom bindings.
     * Unsupported axes/buttons remain unbound for manual configuration.
     */
    public static JoystickConfig forController(Controller controller) {
        JoystickConfig config = new JoystickConfig(controller.getName());
        config.reset(controller);
        return config;
    }

    /** Restores the backend's standard bindings and removes any manual axis inversion. */
    public void reset(Controller controller) {
        reset();
        ControllerMapping mapping = controller.getMapping();
        if (mapping != null) {
            xAxis = validAxis(controller, mapping.axisLeftX);
            yAxis = validAxis(controller, mapping.axisLeftY);
            button1 = validButton(controller, mapping.buttonA);
            button2 = validButton(controller, mapping.buttonB);
            button3 = validButton(controller, mapping.buttonX);
        }
    }

    private static int validAxis(Controller controller, int axis) {
        return axis >= 0 && axis < controller.getAxisCount() ? axis : -1;
    }

    private static int validButton(Controller controller, int button) {
        return button >= 0 && button >= controller.getMinButtonIndex()
            && button <= controller.getMaxButtonIndex() ? button : -1;
    }

    /**
     * Assigns a gameplay action, swapping an occupied binding to that action's old button.
     * Allows A/B/X layouts to be exchanged without first unbinding a required action.
     * @param action gameplay action number, from 1 to 3
     * @param button nonnegative backend index chosen during capture
     */
    public void bindButton(int action, int button) {
        if (action < 1 || action > 3 || button < 0) throw new IllegalArgumentException("Invalid action binding");
        int old = action == 1 ? button1 : action == 2 ? button2 : button3;
        if (action != 1 && button1 == button) button1 = old;
        if (action != 2 && button2 == button) button2 = old;
        if (action != 3 && button3 == button) button3 = old;
        if (action == 1) button1 = button;
        else if (action == 2) button2 = button;
        else button3 = button;
    }

    /** Whether the original two-action stick setup is complete; player switching remains optional. */
    public boolean isConfigured() {
        return xAxis != -1 && yAxis != -1 && button1 != -1 && button2 != -1;
    }

    /** Clears manual bindings and calibration for a fresh setup. */
    public void reset() {
        xAxis = -1;
        yAxis = -1;
        button1 = -1;
        button2 = -1;
        button3 = -1;
        invertX = false;
        invertY = false;
        deadZone = 0.3f;
    }
}
