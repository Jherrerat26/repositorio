package com.example.carritobluetooth;

import java.util.Objects;

/** Used only after a Bluetooth write succeeds, on the serial sender thread. */
public final class MovementTracker {
    private String session;
    private String previousCommand = "S";
    private int previousSpeed;

    public boolean shouldRecord(String command, int speed, String sessionId) {
        if (!Objects.equals(session, sessionId)) {
            session = sessionId;
            previousCommand = "S";
            previousSpeed = 0;
        }
        if (command.equals(previousCommand) && speed == previousSpeed) return false;
        previousCommand = command;
        previousSpeed = speed;
        return true;
    }

    public static String direction(String command) {
        switch (command) {
            case "F": return "Adelante";
            case "B": return "Atrás";
            // Preserve the existing app's calibrated button-to-wire mapping.
            case "R": return "Izquierda";
            case "L": return "Derecha";
            default: return "Detener";
        }
    }
}
