package dev.slimescout;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.slimescout.ObservationTracker.Kind.*;

class ObservationTrackerTest {
    @Test void chunkUnloadClearsOnlyThatChunksEntities() {
        var tracker = new ObservationTracker();
        var a = UUID.randomUUID(); var b = UUID.randomUUID();
        tracker.observe(a, 1L); tracker.observe(b, 2L);
        tracker.unload(1L);
        assertEquals(FIRST, tracker.observe(a, 1L));
        assertEquals(NONE, tracker.observe(b, 2L));
    }
    @Test void repeatedTicksDoNotCountAndMovementIsNotFirstSighting() {
        var tracker = new ObservationTracker(); var slime = UUID.randomUUID();
        assertEquals(FIRST, tracker.observe(slime, -1L));
        assertEquals(NONE, tracker.observe(slime, -1L));
        assertEquals(MOVED, tracker.observe(slime, 0L));
        assertEquals(MOVED, tracker.observe(slime, -1L));
        assertEquals(FIRST, tracker.observe(UUID.randomUUID(), -1L));
    }
    @Test void unloadAndWorldChangeReleaseEntityHistory() {
        var tracker = new ObservationTracker(); var slime = UUID.randomUUID();
        tracker.observe(slime, 42L);
        tracker.retain(Set.of(slime));
        assertEquals(NONE, tracker.observe(slime, 42L));
        tracker.retain(Set.of());
        assertEquals(FIRST, tracker.observe(slime, 42L));
        tracker.clear();
        assertEquals(FIRST, tracker.observe(slime, 42L));
    }
}
