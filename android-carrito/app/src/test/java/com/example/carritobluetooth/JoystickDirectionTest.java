package com.example.carritobluetooth;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class JoystickDirectionTest {
    @Test public void centerAndDeadZoneAlwaysStop() {
        assertEquals("S", JoystickDirection.command(0, 0, 100));
        assertEquals("S", JoystickDirection.command(21, 0, 100));
        assertEquals("S", JoystickDirection.command(0, -22, 100));
        assertEquals("S", JoystickDirection.command(15, 15, 100));
        assertEquals("F", JoystickDirection.command(0, -23, 100));
    }

    @Test public void cardinalAxesPreserveFirmwareCalibration() {
        assertEquals("F", JoystickDirection.command(0, -70, 100));
        assertEquals("B", JoystickDirection.command(0, 70, 100));
        assertEquals("R", JoystickDirection.command(-70, 0, 100));
        assertEquals("L", JoystickDirection.command(70, 0, 100));
    }

    @Test public void dominantAxisAvoidsUnsupportedDiagonalCommands() {
        assertEquals("F", JoystickDirection.command(40, -60, 100));
        assertEquals("R", JoystickDirection.command(-60, -40, 100));
        assertEquals("B", JoystickDirection.command(60, 60, 100));
        assertEquals("L", JoystickDirection.command(500, 20, 100));
    }

    @Test public void zeroSizeOrInvalidCoordinatesCannotDrive() {
        assertEquals("S", JoystickDirection.command(50, 50, 0));
        assertEquals("S", JoystickDirection.command(50, 50, -1));
        assertEquals("S", JoystickDirection.command(Float.NaN, 50, 100));
        assertEquals("S", JoystickDirection.command(50, Float.POSITIVE_INFINITY, 100));
        assertEquals("S", JoystickDirection.command(50, 50, Float.NaN));
    }

    @Test public void deadZoneScalesWithScreenSize() {
        assertEquals("S", JoystickDirection.command(0, -40, 200));
        assertEquals("F", JoystickDirection.command(0, -50, 200));
    }
}
