package com.ygames.ysoccer.framework;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.controllers.Controller;

import java.util.ArrayList;

/** Local/virtual player slots, retaining assigned joystick objects across hardware reconnection. */
public class InputDeviceList extends ArrayList<InputDevice> {

    /** Whether the backend controller already owns a slot; avoids reloading settings every frame. */
    public boolean hasController(Controller controller) {
        return findController(controller) != null;
    }

    /** Returns the live slot for a physical controller, or null during rebinding or before discovery. */
    public InputDevice findController(Controller controller) {
        for (InputDevice device : this) {
            if (device instanceof Joystick && ((Joystick) device).usesController(controller)) return device;
        }
        return null;
    }

    /**
     * Adds new hardware or restores a disconnected slot, preserving team/player object references.
     * Unique IDs win; a model-name fallback supports backends that change IDs after reconnecting.
     * Connected devices are never displaced, including two identical Xbox controllers.
     */
    public void connectController(Controller controller, JoystickConfig config) {
        if (hasController(controller)) return;
        Joystick sameModel = null;
        int port = 0;
        for (InputDevice device : this) {
            if (!(device instanceof Joystick)) continue;
            Joystick joystick = (Joystick) device;
            port = Math.max(port, joystick.port + 1);
            if (!joystick.isConnected()) {
                if (joystick.hasSameId(controller)) {
                    joystick.reconnect(controller, config);
                    return;
                }
                if (sameModel == null && joystick.hasSameModel(controller)) sameModel = joystick;
            }
        }
        if (sameModel != null) {
            sameModel.reconnect(controller, config);
        } else {
            Joystick joystick = new Joystick(controller, config, port);
            joystick.setAvailable(true);
            add(joystick);
        }
    }

    /** Whether any connected controller requested a pause toggle on this input frame. */
    public boolean pauseDown() {
        for (InputDevice device : this) {
            if (device.isConnected() && device.pauseDown()) return true;
        }
        return false;
    }

    public void setAvailability(boolean n) {
        for (InputDevice inputDevice : this) {
            inputDevice.available = n;
        }
    }

    /** Counts unassigned, connected devices that can still join a team. */
    public int getAvailabilityCount() {
        int n = 0;
        for (InputDevice inputDevice : this) {
            if (inputDevice.available && inputDevice.isConnected()) n += 1;
        }
        return n;
    }

    /** Reserves the first connected free slot, or returns null when every usable device is assigned. */
    public InputDevice assignFirstAvailable() {
        for (InputDevice inputDevice : this) {
            if (inputDevice.available && inputDevice.isConnected()) {
                inputDevice.available = false;
                return inputDevice;
            }
        }
        return null;
    }

    /** Advances a player's assignment to the next connected free slot, without wrapping. */
    public InputDevice assignNextAvailable(InputDevice current) {
        int start = indexOf(current);
        if (start == -1) {
            throw new GdxRuntimeException("item not found");
        }

        int len = size();
        for (int i = start + 1; i < len; i++) {
            InputDevice next = get(i);
            if (next.available && next.isConnected()) {
                current.setAvailable(true);
                next.setAvailable(false);
                return next;
            }
        }
        return null;
    }

    /** Rotates an assignment through connected free slots; preserves the current slot if none can be used. */
    public InputDevice rotateAvailable(InputDevice inputDevice, int n) {
        int index = this.indexOf(inputDevice);
        if (index == -1) {
            throw new GdxRuntimeException("item not found");
        }
        InputDevice current = inputDevice;
        current.available = true;
        for (int i = 0; i < size(); i++) {
            index = EMath.rotate(index, 0, this.size() - 1, n);
            inputDevice = this.get(index);
            if (inputDevice.available && inputDevice.isConnected()) {
                inputDevice.available = false;
                return inputDevice;
            }
        }
        // If every device is disconnected, retain the assignment until hardware returns.
        current.available = false;
        return current;
    }
}
