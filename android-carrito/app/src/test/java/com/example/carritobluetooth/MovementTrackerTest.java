package com.example.carritobluetooth;

import org.junit.Test;
import static org.junit.Assert.*;

public class MovementTrackerTest {
    @Test public void heartbeatDoesNotDuplicateMovementButReleaseIsRecorded() {
        MovementTracker tracker = new MovementTracker();
        assertFalse(tracker.shouldRecord("S", 0, "trip"));
        assertTrue(tracker.shouldRecord("F", 140, "trip"));
        for (int i = 0; i < 50; i++) assertFalse(tracker.shouldRecord("F", 140, "trip"));
        assertTrue(tracker.shouldRecord("S", 0, "trip"));
        assertFalse(tracker.shouldRecord("S", 0, "trip"));
        assertTrue(tracker.shouldRecord("F", 140, "trip"));
    }

    @Test public void speedDirectionAndReconnectAreDistinctEvents() {
        MovementTracker tracker = new MovementTracker();
        assertTrue(tracker.shouldRecord("F", 140, "a"));
        assertTrue(tracker.shouldRecord("F", 180, "a"));
        assertTrue(tracker.shouldRecord("R", 180, "a"));
        assertTrue(tracker.shouldRecord("R", 180, "b"));
        assertFalse(tracker.shouldRecord("S", 0, "c"));
        assertTrue(tracker.shouldRecord("R", 180, "c"));
    }

    @Test public void labelsMatchTheExistingHardwareMapping() {
        assertEquals("Izquierda", MovementTracker.direction("R"));
        assertEquals("Derecha", MovementTracker.direction("L"));
        assertEquals("Adelante", MovementTracker.direction("F"));
        assertEquals("Atrás", MovementTracker.direction("B"));
        assertEquals("Detener", MovementTracker.direction("S"));
    }
}
