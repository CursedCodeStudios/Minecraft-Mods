package dev.monumentscout;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ElderObservationTest {
    @Test void partialCountsIncludingZeroNeverBecomeConfirmed() {
        var observation = new ElderObservation();
        for (int count : new int[]{3, 2, 1, 0}) {
            for (int tick = 0; tick < 200; tick++) observation.observe(false, count);
            assertEquals(-1, observation.confirmedCount());
        }
    }
    @Test void unloadOrCountChangeRequiresANewSettlingPeriod() {
        var observation = new ElderObservation();
        for (int tick = 0; tick < 100; tick++) observation.observe(true, 3);
        assertEquals(3, observation.confirmedCount());
        observation.observe(false, 2); assertEquals(-1, observation.confirmedCount());
        for (int tick = 0; tick < 99; tick++) observation.observe(true, 2);
        assertEquals(-1, observation.confirmedCount());
        observation.observe(true, 2); assertEquals(2, observation.confirmedCount());
        observation.observe(true, 0); assertEquals(-1, observation.confirmedCount());
        for (int tick = 0; tick < 99; tick++) observation.observe(true, 0);
        assertEquals(0, observation.confirmedCount());
    }
    @Test void elderMarginChunksAreIncludedInUnloadInvalidation() {
        var bounds = new MonumentBounds(0, 0);
        assertFalse(bounds.containsChunk(-3, 0));
        assertTrue(bounds.containsElderChunk(-3, 0));
        assertTrue(bounds.containsElderChunk(2, 0));
        assertFalse(bounds.containsElderChunk(3, 0));
    }
}
