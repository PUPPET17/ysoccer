package com.ygames.ysoccer.network.mappers;

import com.ygames.ysoccer.framework.InputDevice;
import com.ygames.ysoccer.framework.NetworkInputDevice;
import com.ygames.ysoccer.network.dto.InputDeviceDto;

public class InputDeviceMapper {

    public static InputDeviceDto toDto(InputDevice device) {
        InputDeviceDto dto = new InputDeviceDto();
        dto.x0 = device.x0;
        dto.y0 = device.y0;
        dto.fire10 = device.fire10;
        dto.fire20 = device.fire20;
        dto.fire30 = device.fire30;
        return dto;
    }

    public static void updateFromDto(NetworkInputDevice device, InputDeviceDto dto) {
        device.x0 = dto.x0;
        device.y0 = dto.y0;
        device.fire10 = dto.fire10;
        device.fire20 = dto.fire20;
        device.fire30 = dto.fire30;
    }
}
