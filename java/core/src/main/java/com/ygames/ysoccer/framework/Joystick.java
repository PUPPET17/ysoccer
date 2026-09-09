package com.ygames.ysoccer.framework;

import com.badlogic.gdx.controllers.Controller;

import static java.lang.Math.round;

class Joystick extends InputDevice {

    private Controller controller;
    private JoystickConfig config;

    Joystick(Controller controller, JoystickConfig config, int port) {
        super(Type.JOYSTICK, port);
        this.controller = controller;
        this.config = config;
    }

    @Override
    protected void read() {
        x0 = round(this.controller.getAxis(config.xAxis));
        y0 = round(this.controller.getAxis(config.yAxis));
        fire10 = this.controller.getButton(config.button1);
        fire20 = this.controller.getButton(config.button2);
        fire30 = config.button3 >= 0 && this.controller.getButton(config.button3);
    }
}
