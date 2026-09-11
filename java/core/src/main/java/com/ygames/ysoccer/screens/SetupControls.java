package com.ygames.ysoccer.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Texture;
import com.ygames.ysoccer.framework.GLGame;
import com.ygames.ysoccer.framework.GLScreen;
import com.ygames.ysoccer.framework.InputDevice;
import com.ygames.ysoccer.framework.InputDeviceConfig;
import com.ygames.ysoccer.framework.JoystickConfig;
import com.ygames.ysoccer.framework.KeyboardConfig;
import com.ygames.ysoccer.gui.Button;
import com.ygames.ysoccer.gui.Widget;
import com.ygames.ysoccer.framework.EMath;

import java.util.ArrayList;
import java.util.Arrays;

import static com.ygames.ysoccer.framework.Assets.font14;
import static com.ygames.ysoccer.framework.Assets.gettext;
import static com.ygames.ysoccer.framework.Font.Align.CENTER;

/** Edits model-level bindings while capturing input only from the selected physical controller. */
class SetupControls extends GLScreen {

    private enum ConfigParam {KEY_LEFT, KEY_RIGHT, KEY_UP, KEY_DOWN, BUTTON_1, BUTTON_2, BUTTON_3}

    private final ConfigParam[] buttonParams = {
        ConfigParam.BUTTON_1, ConfigParam.BUTTON_2, ConfigParam.BUTTON_3
    };
    private final ConfigParam[] axisParams = {ConfigParam.KEY_LEFT, ConfigParam.KEY_RIGHT, ConfigParam.KEY_UP, ConfigParam.KEY_DOWN};

    private InputDeviceButton selectedInputDeviceButton;
    private final ArrayList<JoystickConfig> joystickConfigs;
    private final ArrayList<KeyboardConfig> keyboardConfigs;
    private final InputProcessor inputProcessor;
    private final JoystickListener joystickListener;
    private ConfigButton listeningConfigButton;
    private boolean[] neutralAxes;
    private boolean refreshDevices;
    /** Shows the directions actually delivered to gameplay, including saved axis inversion. */
    private final ControllerDirectionLabel directionLabel;

    SetupControls(GLGame game) {
        super(game);
        background = new Texture("images/backgrounds/menu_controls.jpg");

        inputProcessor = new SetupInputProcessor();
        joystickConfigs = new ArrayList<>();
        joystickListener = new JoystickListener();
        Controllers.addListener(joystickListener);
        listeningConfigButton = null;

        Widget w;

        w = new TitleBar(gettext("CONTROLS"), 0x83079C);
        widgets.add(w);

        int pos = 0;
        keyboardConfigs = game.settings.getKeyboardConfigs();
        for (int port = 0; port < 2; port++) {
            InputDeviceButton inputDeviceButton = new InputDeviceButton(keyboardConfigs.get(port), port, pos);
            widgets.add(inputDeviceButton);

            if (port == 0) {
                selectedInputDeviceButton = inputDeviceButton;
                setSelectedWidget(inputDeviceButton);
            }
            pos++;
        }

        int port = 0;
        for (Controller controller : Controllers.getControllers()) {

            // search previously saved configuration
            JoystickConfig joystickConfig = game.settings.getJoystickConfigByName(controller.getName());
            if (joystickConfig == null) {
                joystickConfig = JoystickConfig.forController(controller);
            }
            // Identical models share settings, but capture is restricted to the selected hardware.
            for (JoystickConfig existing : joystickConfigs) {
                if (existing.name.equals(joystickConfig.name)) joystickConfig = existing;
            }

            joystickConfigs.add(joystickConfig);

            InputDeviceButton inputDeviceButton = new InputDeviceButton(joystickConfig, port, pos, controller);
            widgets.add(inputDeviceButton);

            w = new ResetJoystickButton(inputDeviceButton);
            widgets.add(w);

            port++;
            pos++;
        }

        w = new InputDeviceLabel();
        widgets.add(w);

        w = new LeftLabel();
        widgets.add(w);

        w = new LeftButton();
        widgets.add(w);

        w = new RightLabel();
        widgets.add(w);

        w = new RightButton();
        widgets.add(w);

        w = new UpLabel();
        widgets.add(w);

        w = new UpButton();
        widgets.add(w);

        w = new DownLabel();
        widgets.add(w);

        w = new DownButton();
        widgets.add(w);

        w = new FireLabel(1);
        widgets.add(w);

        w = new FireButton(1);
        widgets.add(w);

        w = new FireLabel(2);
        widgets.add(w);

        w = new FireButton(2);
        widgets.add(w);

        w = new FireLabel(3);
        widgets.add(w);

        w = new FireButton(3);
        widgets.add(w);

        w = new DeadZoneButton();
        widgets.add(w);

        widgets.add(new InvertAxisButton(true));
        widgets.add(new InvertAxisButton(false));
        directionLabel = new ControllerDirectionLabel();
        widgets.add(directionLabel);

        w = new ControllerHint();
        widgets.add(w);

        w = new ExitButton();
        widgets.add(w);
    }

    /** Rebuilds the device selector after hot-plugging, outside controller event dispatch. */
    @Override
    public void render(float delta) {
        if (refreshDevices) {
            if (listeningConfigButton != null) listeningConfigButton.quitEntryMode();
            game.setScreen(new SetupControls(game));
            dispose();
            return;
        }
        directionLabel.setDirty(true);
        super.render(delta);
    }

    /** Releases capture and listeners on every exit path, including a device-list refresh. */
    @Override
    public void hide() {
        Controllers.removeListener(joystickListener);
        if (listeningConfigButton != null) listeningConfigButton.quitEntryMode();
    }

    private class InputDeviceButton extends Button {

        private final InputDeviceConfig config;
        private final int port;
        /** Physical source for rebinding; null for keyboard configurations. */
        private final Controller controller;

        InputDeviceButton(InputDeviceConfig config, int port, int pos) {
            this(config, port, pos, null);
        }

        InputDeviceButton(InputDeviceConfig config, int port, int pos, Controller controller) {
            this.config = config;
            this.port = port;
            this.controller = controller;
            setGeometry(game.gui.WIDTH / 2 - 560, 180 + 46 * pos, 240, 42);
            switch (config.type) {
                case KEYBOARD:
                    setText(gettext("KEYBOARD") + " " + (port + 1), CENTER, font14);
                    break;

                case JOYSTICK:
                    setText(gettext("JOYSTICK") + " " + (port + 1), CENTER, font14);
                    break;
            }
        }

        @Override
        public void refresh() {
            setColor(this == selectedInputDeviceButton ? 0x2F2F5E : 0x484891);
            switch (config.type) {
                case KEYBOARD:
                    break;

                case JOYSTICK:
                    if (!((JoystickConfig) config).isConfigured()) {
                        setColor(this == selectedInputDeviceButton ? 0x800000 : 0xB40000);
                    }
                    break;
            }
        }

        @Override
        public void onFire1Down() {
            selectedInputDeviceButton = this;
            for (Widget widget : widgets) {
                widget.setDirty(true);
            }
        }
    }

    private class ResetJoystickButton extends Button {

        final InputDeviceButton inputDeviceButton;

        ResetJoystickButton(InputDeviceButton inputDeviceButton) {
            this.inputDeviceButton = inputDeviceButton;
            setGeometry(inputDeviceButton.x + inputDeviceButton.w + 2, inputDeviceButton.y, 38, 42);
            setColor(0xB40000);
            setText("" + (char) 19, CENTER, font14);
        }

        @Override
        public void refresh() {
            setVisible(inputDeviceButton == selectedInputDeviceButton && ((JoystickConfig) inputDeviceButton.config).isConfigured());
        }

        @Override
        public void onFire1Down() {
            ((JoystickConfig) inputDeviceButton.config).reset(inputDeviceButton.controller);
            setJoystickConfigs();
            game.reloadInputDevices();
            refreshAllWidgets();
        }
    }

    private class InputDeviceLabel extends Button {

        InputDeviceLabel() {
            setGeometry((game.gui.WIDTH - 760) / 2, 100, 760, 40);
            setColor(0x404040);
            setText("", CENTER, font14);
            setActive(false);
        }

        @Override
        public void refresh() {
            switch (selectedInputDeviceButton.config.type) {
                case KEYBOARD:
                    setText("");
                    setVisible(false);
                    break;

                case JOYSTICK:
                    setText(((JoystickConfig) selectedInputDeviceButton.config).name.toUpperCase());
                    setVisible(true);
                    break;
            }
        }
    }

    private abstract class ConfigButton extends Button {

        ConfigParam configParam;

        void setKeyCode(int keyCode) {
            if (isKeyCodeReserved(keyCode)) return;

            if (keyCode != Input.Keys.ESCAPE && selectedInputDeviceButton.config.type == InputDevice.Type.KEYBOARD) {
                KeyboardConfig keyboardConfig = (KeyboardConfig) selectedInputDeviceButton.config;
                switch (configParam) {
                    case KEY_LEFT:
                        if (isKeyCodeAssigned(keyCode, configParam, selectedInputDeviceButton.port)) {
                            return;
                        }
                        keyboardConfig.keyLeft = keyCode;
                        break;

                    case KEY_RIGHT:
                        if (isKeyCodeAssigned(keyCode, configParam, selectedInputDeviceButton.port)) {
                            return;
                        }
                        keyboardConfig.keyRight = keyCode;
                        break;

                    case KEY_UP:
                        if (isKeyCodeAssigned(keyCode, configParam, selectedInputDeviceButton.port)) {
                            return;
                        }
                        keyboardConfig.keyUp = keyCode;
                        break;

                    case KEY_DOWN:
                        if (isKeyCodeAssigned(keyCode, configParam, selectedInputDeviceButton.port)) {
                            return;
                        }
                        keyboardConfig.keyDown = keyCode;
                        break;

                    case BUTTON_1:
                        if (isKeyCodeAssigned(keyCode, configParam, selectedInputDeviceButton.port)) {
                            return;
                        }
                        keyboardConfig.button1 = keyCode;
                        break;

                    case BUTTON_2:
                        if (isKeyCodeAssigned(keyCode, configParam, selectedInputDeviceButton.port)) {
                            return;
                        }
                        keyboardConfig.button2 = keyCode;
                        break;

                    case BUTTON_3:
                        if (isKeyCodeAssigned(keyCode, configParam, selectedInputDeviceButton.port)) {
                            return;
                        }
                        keyboardConfig.button3 = keyCode;
                        break;
                }
            }
            setKeyboardConfigs();
            quitEntryMode();
        }

        /** Applies a deliberate axis deflection or button press; axis polarity follows the requested direction. */
        void setJoystickConfigParam(int axisIndex, int buttonIndex, float value) {
            JoystickConfig joystickConfig = (JoystickConfig) selectedInputDeviceButton.config;
            switch (configParam) {
                case KEY_LEFT:
                case KEY_RIGHT:
                    if (axisIndex == -1) return;
                    if (axisIndex == joystickConfig.yAxis) return;
                    joystickConfig.xAxis = axisIndex;
                    joystickConfig.invertX = (value > 0) == (configParam == ConfigParam.KEY_LEFT);
                    break;

                case KEY_UP:
                case KEY_DOWN:
                    if (axisIndex == -1) return;
                    if (axisIndex == joystickConfig.xAxis) return;
                    joystickConfig.yAxis = axisIndex;
                    joystickConfig.invertY = (value > 0) == (configParam == ConfigParam.KEY_UP);
                    break;

                case BUTTON_1:
                    if (buttonIndex == -1) return;
                    joystickConfig.bindButton(1, buttonIndex);
                    break;

                case BUTTON_2:
                    if (buttonIndex == -1) return;
                    joystickConfig.bindButton(2, buttonIndex);
                    break;

                case BUTTON_3:
                    if (buttonIndex == -1) return;
                    joystickConfig.bindButton(3, buttonIndex);
                    break;
            }
            setJoystickConfigs();
            quitEntryMode();
        }

        private void quitEntryMode() {
            entryMode = false;
            listeningConfigButton = null;
            neutralAxes = null;
            game.capturingControls = false;
            game.reloadInputDevices();
            Gdx.input.setInputProcessor(null);
            refreshAllWidgets();
        }

        @Override
        public void onFire1Down() {
            if (!entryMode) {
                entryMode = true;
                listeningConfigButton = this;
                game.capturingControls = true;
                Controller controller = selectedInputDeviceButton.controller;
                if (controller != null) {
                    neutralAxes = new boolean[controller.getAxisCount()];
                    for (int i = 0; i < neutralAxes.length; i++) {
                        neutralAxes[i] = Math.abs(controller.getAxis(i)) < 0.25f;
                    }
                }
                game.inputDevices.clear();
                Gdx.input.setInputProcessor(inputProcessor);
                refreshAllWidgets();
            }
        }
    }

    private void setKeyboardConfigs() {
        game.settings.setKeyboardConfigs(keyboardConfigs);
    }

    private void setJoystickConfigs() {
        game.settings.setJoystickConfigs(joystickConfigs);
    }

    private boolean isKeyCodeAssigned(int keyCode, ConfigParam configParam, int port) {
        for (int i = 0; i < 2; i++) {
            KeyboardConfig config = keyboardConfigs.get(i);
            if (config.keyLeft == keyCode && (configParam != ConfigParam.KEY_LEFT || port != i))
                return true;
            if (config.keyRight == keyCode && (configParam != ConfigParam.KEY_RIGHT || port != i))
                return true;
            if (config.keyUp == keyCode && (configParam != ConfigParam.KEY_UP || port != i))
                return true;
            if (config.keyDown == keyCode && (configParam != ConfigParam.KEY_DOWN || port != i))
                return true;
            if (config.button1 == keyCode && (configParam != ConfigParam.BUTTON_1 || port != i))
                return true;
            if (config.button2 == keyCode && (configParam != ConfigParam.BUTTON_2 || port != i))
                return true;
            if (config.button3 == keyCode && (configParam != ConfigParam.BUTTON_3 || port != i))
                return true;
        }
        return false;
    }


    private boolean isKeyCodeReserved(int keyCode) {
        Integer[] reservedKeyCodes = {
                Input.Keys.SPACE, Input.Keys.R, Input.Keys.P, Input.Keys.H, Input.Keys.APOSTROPHE,
                Input.Keys.F1, Input.Keys.F2, Input.Keys.F3, Input.Keys.F4,
                Input.Keys.F5, Input.Keys.F6, Input.Keys.F7, Input.Keys.F8,
                Input.Keys.F9, Input.Keys.F10, Input.Keys.F11, Input.Keys.F12
        };
        return Arrays.asList(reservedKeyCodes).contains(keyCode);
    }

    private class LeftLabel extends Button {

        LeftLabel() {
            setGeometry((game.gui.WIDTH - 200) / 2 - 150, 340, 200, 40);
            setText(gettext("CONTROLS.LEFT"), CENTER, font14);
            setColor(0x404040);
            setActive(false);
        }
    }

    private class LeftButton extends ConfigButton {

        LeftButton() {
            configParam = ConfigParam.KEY_LEFT;
            setGeometry((game.gui.WIDTH - 200) / 2 - 150, 380, 200, 46);
            setText("", CENTER, font14);
        }

        @Override
        public void refresh() {
            switch (selectedInputDeviceButton.config.type) {
                case KEYBOARD:
                    KeyboardConfig keyboardConfig = (KeyboardConfig) selectedInputDeviceButton.config;
                    if (entryMode) {
                        setText("?");
                        setColor(0xEB9532);
                    } else {
                        setText(Input.Keys.toString(keyboardConfig.keyLeft).toUpperCase());
                        setColor(0x548854);
                    }
                    break;

                case JOYSTICK:
                    JoystickConfig joystickConfig = (JoystickConfig) selectedInputDeviceButton.config;
                    int xAxisIndex = joystickConfig.xAxis;
                    if (entryMode) {
                        setText("?");
                        setColor(0xEB9532);
                    } else if (xAxisIndex == -1) {
                        setText(gettext("CONTROLS.UNKNOWN"));
                        setColor(0xB40000);
                    } else {
                        setText(gettext("CONTROLS.AXIS") + " " + xAxisIndex);
                        setColor(0x548854);
                    }
                    break;
            }
        }
    }

    private class RightLabel extends Button {

        RightLabel() {
            setGeometry((game.gui.WIDTH - 200) / 2 + 150, 340, 200, 40);
            setText(gettext("CONTROLS.RIGHT"), CENTER, font14);
            setColor(0x404040);
            setActive(false);
        }
    }

    private class RightButton extends ConfigButton {

        RightButton() {
            configParam = ConfigParam.KEY_RIGHT;
            setGeometry((game.gui.WIDTH - 200) / 2 + 150, 380, 200, 46);
            setText("", CENTER, font14);
        }

        @Override
        public void refresh() {
            switch (selectedInputDeviceButton.config.type) {
                case KEYBOARD:
                    KeyboardConfig keyboardConfig = (KeyboardConfig) selectedInputDeviceButton.config;
                    if (entryMode) {
                        setText("?");
                        setColor(0xEB9532);
                    } else {
                        setText(Input.Keys.toString(keyboardConfig.keyRight).toUpperCase());
                        setColor(0x548854);
                    }
                    break;

                case JOYSTICK:
                    JoystickConfig joystickConfig = (JoystickConfig) selectedInputDeviceButton.config;
                    int xAxisIndex = joystickConfig.xAxis;
                    if (entryMode) {
                        setText("?");
                        setColor(0xEB9532);
                    } else if (xAxisIndex == -1) {
                        setText(gettext("CONTROLS.UNKNOWN"));
                        setColor(0xB40000);
                    } else {
                        setText(gettext("CONTROLS.AXIS") + " " + xAxisIndex);
                        setColor(0x548854);
                    }
                    break;
            }
        }
    }

    private class UpLabel extends Button {

        UpLabel() {
            setGeometry((game.gui.WIDTH - 200) / 2, 180, 200, 40);
            setText(gettext("CONTROLS.UP"), CENTER, font14);
            setColor(0x404040);
            setActive(false);
        }
    }

    private class UpButton extends ConfigButton {

        UpButton() {
            configParam = ConfigParam.KEY_UP;
            setGeometry((game.gui.WIDTH - 200) / 2, 220, 200, 46);
            setText("", CENTER, font14);
        }

        @Override
        public void refresh() {
            switch (selectedInputDeviceButton.config.type) {
                case KEYBOARD:
                    KeyboardConfig keyboardConfig = (KeyboardConfig) selectedInputDeviceButton.config;
                    if (entryMode) {
                        setText("?");
                        setColor(0xEB9532);
                    } else {
                        setText(Input.Keys.toString(keyboardConfig.keyUp).toUpperCase());
                        setColor(0x548854);
                    }
                    break;

                case JOYSTICK:
                    JoystickConfig joystickConfig = (JoystickConfig) selectedInputDeviceButton.config;
                    int yAxisIndex = joystickConfig.yAxis;
                    if (entryMode) {
                        setText("?");
                        setColor(0xEB9532);
                    } else if (yAxisIndex == -1) {
                        setText(gettext("CONTROLS.UNKNOWN"));
                        setColor(0xB40000);
                    } else {
                        setText(gettext("CONTROLS.AXIS") + " " + yAxisIndex);
                        setColor(0x548854);
                    }
                    break;
            }
        }
    }

    private class DownLabel extends Button {

        DownLabel() {
            setGeometry((game.gui.WIDTH - 200) / 2, 500, 200, 40);
            setText(gettext("CONTROLS.DOWN"), CENTER, font14);
            setColor(0x404040);
            setActive(false);
        }
    }

    private class DownButton extends ConfigButton {

        DownButton() {
            configParam = ConfigParam.KEY_DOWN;
            setGeometry((game.gui.WIDTH - 200) / 2, 540, 200, 46);
            setText("", CENTER, font14);
        }

        @Override
        public void refresh() {
            switch (selectedInputDeviceButton.config.type) {
                case KEYBOARD:
                    KeyboardConfig keyboardConfig = (KeyboardConfig) selectedInputDeviceButton.config;
                    if (entryMode) {
                        setText("?");
                        setColor(0xEB9532);
                    } else {
                        setText(Input.Keys.toString(keyboardConfig.keyDown).toUpperCase());
                        setColor(0x548854);
                    }
                    break;

                case JOYSTICK:
                    JoystickConfig joystickConfig = (JoystickConfig) selectedInputDeviceButton.config;
                    int yAxisIndex = joystickConfig.yAxis;
                    if (entryMode) {
                        setText("?");
                        setColor(0xEB9532);
                    } else if (yAxisIndex == -1) {
                        setText(gettext("CONTROLS.UNKNOWN"));
                        setColor(0xB40000);
                    } else {
                        setText(gettext("CONTROLS.AXIS") + " " + yAxisIndex);
                        setColor(0x548854);
                    }
                    break;
            }
        }
    }

    private class FireLabel extends Button {

        FireLabel(int buttonNumber) {
            setGeometry((game.gui.WIDTH - 200) / 2 + 420, 205 + (135 * (buttonNumber - 1)), 200, 40);
            if (buttonNumber == 3) {
                setText(gettext("CONTROLS.SWITCH PLAYER"), CENTER, font14);
            } else {
                setText(gettext("CONTROLS.BUTTON") + " " + ((buttonNumber == 1) ? "A" : "B"), CENTER, font14);
            }
            setColor(0x404040);
            setActive(false);
        }
    }

    private class FireButton extends ConfigButton {

        final int buttonNumber;

        FireButton(int buttonNumber) {
            this.buttonNumber = buttonNumber;
            configParam = buttonNumber == 1 ? ConfigParam.BUTTON_1
                : buttonNumber == 2 ? ConfigParam.BUTTON_2 : ConfigParam.BUTTON_3;
            setGeometry((game.gui.WIDTH - 200) / 2 + 420, 245 + (135 * (buttonNumber - 1)), 200, 46);
            setText("", CENTER, font14);
        }

        @Override
        public void refresh() {
            switch (selectedInputDeviceButton.config.type) {
                case KEYBOARD:
                    KeyboardConfig keyboardConfig = (KeyboardConfig) selectedInputDeviceButton.config;
                    int value = buttonNumber == 1 ? keyboardConfig.button1
                        : buttonNumber == 2 ? keyboardConfig.button2 : keyboardConfig.button3;
                    if (entryMode) {
                        setText("?");
                        setColor(0xEB9532);
                    } else if (value == -1) {
                        setText(gettext(buttonNumber == 3 ? "CONTROLS.NOT SET" : "CONTROLS.UNKNOWN"));
                        setColor(0xB40000);
                    } else {
                        setText(Input.Keys.toString(value).toUpperCase());
                        setColor(0x548854);
                    }
                    break;

                case JOYSTICK:
                    JoystickConfig joystickConfig = (JoystickConfig) selectedInputDeviceButton.config;
                    int index = buttonNumber == 1 ? joystickConfig.button1
                        : buttonNumber == 2 ? joystickConfig.button2 : joystickConfig.button3;
                    if (entryMode) {
                        setText("?");
                        setColor(0xEB9532);
                    } else if (index == -1) {
                        setText(gettext(buttonNumber == 3 ? "CONTROLS.NOT SET" : "CONTROLS.UNKNOWN"));
                        setColor(0xB40000);
                    } else {
                        setText(buttonDescription(index));
                        setColor(0x548854);
                    }
                    break;
            }
        }
    }

    /** Shows backend-standard face-button names so Xbox users need not interpret numeric codes. */
    private String buttonDescription(int index) {
        ControllerMapping mapping = selectedInputDeviceButton.controller.getMapping();
        if (mapping != null) {
            if (index == mapping.buttonA) return "A";
            if (index == mapping.buttonB) return "B";
            if (index == mapping.buttonX) return "X";
            if (index == mapping.buttonY) return "Y";
            if (index == mapping.buttonL1) return "LB";
            if (index == mapping.buttonR1) return "RB";
            if (index == mapping.buttonStart) return "START";
        }
        return Integer.toString(index);
    }

    /** Adjusts the selected model's drift tolerance using the menu's increase/decrease actions. */
    private class DeadZoneButton extends Button {
        DeadZoneButton() {
            setGeometry((game.gui.WIDTH - 400) / 2, 610, 400, 36);
            setText("", CENTER, font14);
            setColor(0x548854);
            setVisible(false);
        }

        @Override
        public void refresh() {
            setVisible(selectedInputDeviceButton.config.type == InputDevice.Type.JOYSTICK);
            if (visible) {
                JoystickConfig config = (JoystickConfig) selectedInputDeviceButton.config;
                setText(gettext("CONTROLS.DEAD ZONE") + " " + Math.round(config.deadZone * 100) + "%");
            }
        }

        @Override
        public void onFire1Down() { change(5); }

        @Override
        public void onFire2Down() { change(-5); }

        private void change(int step) {
            JoystickConfig config = (JoystickConfig) selectedInputDeviceButton.config;
            config.deadZone = Math.max(10, Math.min(80, Math.round(config.deadZone * 100) + step)) / 100f;
            setJoystickConfigs();
            game.reloadInputDevices();
            setDirty(true);
        }
    }

    /** Makes saved axis polarity visible and correctable without recapturing an axis or resetting actions. */
    private class InvertAxisButton extends Button {
        private final boolean horizontal;

        InvertAxisButton(boolean horizontal) {
            this.horizontal = horizontal;
            setGeometry(game.gui.WIDTH / 2 - 560, horizontal ? 485 : 532, 280, 36);
            setText("", CENTER, font14);
            setColor(0x548854);
            setVisible(false);
        }

        @Override
        public void refresh() {
            setVisible(selectedInputDeviceButton.config.type == InputDevice.Type.JOYSTICK);
            if (!visible) return;
            JoystickConfig config = (JoystickConfig) selectedInputDeviceButton.config;
            boolean inverted = horizontal ? config.invertX : config.invertY;
            setText(gettext(horizontal ? "CONTROLS.HORIZONTAL" : "CONTROLS.VERTICAL") + ": "
                + gettext(inverted ? "CONTROLS.REVERSED" : "CONTROLS.NORMAL"));
        }

        @Override
        public void onFire1Down() { toggle(); }

        @Override
        public void onFire2Down() { toggle(); }

        private void toggle() {
            JoystickConfig config = (JoystickConfig) selectedInputDeviceButton.config;
            if (horizontal) config.invertX = !config.invertX;
            else config.invertY = !config.invertY;
            setJoystickConfigs();
            game.reloadInputDevices();
            refreshAllWidgets();
        }
    }

    /** Preview reads the game slot, rather than independently interpreting raw controller values. */
    private class ControllerDirectionLabel extends Button {
        ControllerDirectionLabel() {
            setGeometry(game.gui.WIDTH / 2 - 560, 580, 280, 26);
            setText("", CENTER, font14);
            setColor(0x404040);
            setActive(false);
            setVisible(false);
        }

        @Override
        public void refresh() {
            setVisible(selectedInputDeviceButton.controller != null);
            if (!visible) return;
            InputDevice input = game.inputDevices.findController(selectedInputDeviceButton.controller);
            String direction = "-";
            if (input != null) {
                String x = input.x0 < 0 ? gettext("CONTROLS.LEFT") : input.x0 > 0 ? gettext("CONTROLS.RIGHT") : "";
                String y = input.y0 < 0 ? gettext("CONTROLS.UP") : input.y0 > 0 ? gettext("CONTROLS.DOWN") : "";
                if (!x.isEmpty() || !y.isEmpty()) direction = (x + " " + y).trim();
            }
            setText(gettext("CONTROLS.DIRECTION") + ": " + direction);
        }
    }

    /** Explains always-available D-pad/pause controls and how to adjust drift tolerance. */
    private class ControllerHint extends Button {
        ControllerHint() {
            setGeometry((game.gui.WIDTH - 1160) / 2, 146, 1160, 26);
            setText(gettext("CONTROLS.GAMEPAD HELP"), CENTER, com.ygames.ysoccer.framework.Assets.font10);
            setColor(0x404040);
            setActive(false);
            setVisible(false);
        }

        @Override
        public void refresh() {
            setVisible(selectedInputDeviceButton.config.type == InputDevice.Type.JOYSTICK);
        }
    }

    private class ExitButton extends Button {

        ExitButton() {
            setColor(0xC84200);
            setGeometry((game.gui.WIDTH - 180) / 2, 660, 180, 36);
            setText(gettext("EXIT"), CENTER, font14);
        }

        @Override
        public void onFire1Up() {
            Controllers.removeListener(joystickListener);
            game.settings.save();
            game.setScreen(new Main(game));
        }
    }

    private class JoystickListener extends ControllerAdapter {

        @Override
        public void connected(Controller controller) {
            refreshDevices = true;
        }

        @Override
        public void disconnected(Controller controller) {
            refreshDevices = true;
        }

        @Override
        public boolean buttonDown(Controller controller, int buttonIndex) {
            if (selectedInputDeviceButton.config.type != InputDevice.Type.JOYSTICK || listeningConfigButton == null) {
                return false;
            }

            if (EMath.isAmong(listeningConfigButton.configParam, buttonParams)) {
                if (controller == selectedInputDeviceButton.controller) {
                    listeningConfigButton.setJoystickConfigParam(-1, buttonIndex, 0);
                }
            }
            return false;
        }

        @Override
        public boolean axisMoved(Controller controller, int axisIndex, float value) {
            if (selectedInputDeviceButton.config.type != InputDevice.Type.JOYSTICK || listeningConfigButton == null) {
                return false;
            }

            if (EMath.isAmong(listeningConfigButton.configParam, axisParams)) {
                if (controller == selectedInputDeviceButton.controller && neutralAxes != null
                    && axisIndex >= 0 && axisIndex < neutralAxes.length) {
                    if (Math.abs(value) < 0.25f) neutralAxes[axisIndex] = true;
                    if (neutralAxes[axisIndex] && Math.abs(value) >= 0.65f) {
                        listeningConfigButton.setJoystickConfigParam(axisIndex, -1, value);
                    }
                }
            }
            return false;
        }
    }

    private class SetupInputProcessor extends InputAdapter {

        public boolean keyUp(int keycode) {
            if (listeningConfigButton != null) listeningConfigButton.setKeyCode(keycode);
            return false;
        }
    }
}
