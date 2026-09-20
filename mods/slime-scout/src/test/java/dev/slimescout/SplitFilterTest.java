package dev.slimescout;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.slimescout.SplitFilter.Verdict.*;

class SplitFilterTest {
    private SplitFilter.Sample slime(UUID id, int size, double x, double y, double z) {
        return new SplitFilter.Sample(id, size, x, y, z, size * 0.52);
    }
    @Test void naturalSmallSlimesAreStillAllowedAfterGracePeriod() {
        var filter = new SplitFilter();
        var natural = slime(UUID.randomUUID(), 1, 10, 20, 10);
        assertEquals(WAIT, filter.observe(natural, 0));
        assertEquals(ALLOW, filter.observe(natural, SplitFilter.GRACE_TICKS));
    }
    @Test void splitAcrossChunkBoundaryIsNotNewEvidence() {
        var filter = new SplitFilter();
        var parent = slime(UUID.randomUUID(), 4, 15.9, 20, 15.9);
        filter.death(parent, 100);
        var child = slime(UUID.randomUUID(), 2, 16.4, 20.5, 16.4);
        assertEquals(SPLIT, filter.observe(child, 101));
        assertEquals(SPLIT, filter.observe(child, 1000));
        assertEquals(1, child.chunkX());
        assertEquals(1, child.chunkZ());
    }
    @Test void buffersChildrenWhoseParentDeathArrivesLater() {
        var filter = new SplitFilter();
        var child = slime(UUID.randomUUID(), 2, 0.5, 20.5, 0.5);
        assertEquals(WAIT, filter.observe(child, 10));
        filter.death(slime(UUID.randomUUID(), 4, 0, 20, 0), 12);
        assertEquals(SPLIT, filter.observe(child, 12));
    }
    @Test void grandchildrenOfSplitSlimesAreAlsoFiltered() {
        var filter = new SplitFilter();
        filter.death(slime(UUID.randomUUID(), 4, 0, 20, 0), 0);
        var child = slime(UUID.randomUUID(), 2, 0.5, 20.5, 0.5);
        assertEquals(SPLIT, filter.observe(child, 1));
        filter.death(child, 10);
        assertEquals(SPLIT, filter.observe(slime(UUID.randomUUID(), 1, 0.75, 21, 0.75), 11));
    }
    @Test void sizeDistanceAndTimePreventUnrelatedSuppression() {
        var filter = new SplitFilter();
        filter.death(slime(UUID.randomUUID(), 4, 0, 20, 0), 0);
        var wrongSize = slime(UUID.randomUUID(), 4, 0, 20.5, 0);
        var distant = slime(UUID.randomUUID(), 2, 20, 20.5, 0);
        var above = slime(UUID.randomUUID(), 2, 0, 40, 0);
        for (var s : List.of(wrongSize, distant, above)) {
            assertEquals(WAIT, filter.observe(s, 1));
            assertEquals(ALLOW, filter.observe(s, 6));
        }
        var late = slime(UUID.randomUUID(), 2, 0, 20.5, 0);
        assertEquals(WAIT, filter.observe(late, 70));
        assertEquals(ALLOW, filter.observe(late, 75));
    }
    @Test void usesFirstPositionAndClearsAcrossWorldChanges() {
        var filter = new SplitFilter(); var id = UUID.randomUUID();
        filter.observe(slime(id, 4, -0.1, 20, 1), 0);
        filter.observe(slime(id, 4, 2, 20, 1), 5);
        assertEquals(-1, filter.first(id).chunkX());
        filter.death(slime(UUID.randomUUID(), 4, 10, 20, 10), 10);
        filter.clear();
        var otherWorld = slime(UUID.randomUUID(), 2, 10, 20.5, 10);
        filter.observe(otherWorld, 11);
        assertEquals(ALLOW, filter.observe(otherWorld, 16));
    }
    @Test void existingSlimeIsNotReclassifiedByLaterNearbyDeath() {
        var filter = new SplitFilter(); var natural = slime(UUID.randomUUID(), 2, 0, 20.5, 0);
        filter.observe(natural, 0);
        assertEquals(ALLOW, filter.observe(natural, 5));
        filter.death(slime(UUID.randomUUID(), 4, 0, 20, 0), 6);
        assertEquals(ALLOW, filter.observe(natural, 6));
    }
}
