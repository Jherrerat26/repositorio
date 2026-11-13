package com.example.carritobluetooth;

/** Joystick geometry without Android dependencies, so the safety zone can be tested. */
final class JoystickDirection {
    static final float DEAD_ZONE = 0.22f;

    static String command(float x, float y, float radius) {
        if (radius <= 0 || Float.isNaN(radius) || Float.isInfinite(radius)
                || Float.isNaN(x) || Float.isNaN(y) || Float.isInfinite(x) || Float.isInfinite(y)) return "S";
        if (Math.hypot(x, y) <= radius * DEAD_ZONE) return "S";
        if (Math.abs(y) >= Math.abs(x)) return y < 0 ? "F" : "B";
        // The existing firmware/app calibration uses R for left and L for right.
        return x < 0 ? "R" : "L";
    }

    private JoystickDirection() {}
}
