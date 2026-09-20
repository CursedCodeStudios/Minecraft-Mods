package dev.monumentscout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static dev.monumentscout.MonumentStore.State.*;

class MonumentStoreTest {
    @TempDir Path folder;
    @Test void partialSightingsAndUnknownSurveysRetainSavedElderCounts() throws Exception {
        var store = new MonumentStore(folder, "world", "minecraft:overworld");
        var bounds = new MonumentBounds(0, 0); store.discover(bounds, false, 1);
        var entry = store.get(bounds); store.update(entry, 24, 3, 2);
        for (int count : new int[]{2, 1, 0, -1}) store.observeElders(entry, count);
        assertEquals(3, entry.elders); assertEquals(24, entry.sponges);
        store.update(entry, 24, -1, 3);
        assertEquals(3, entry.elders); assertFalse(entry.fresh);
        store.save();
        var loaded = new MonumentStore(folder, "world", "minecraft:overworld");
        assertEquals(3, loaded.get(bounds).elders); assertEquals(24, loaded.get(bounds).sponges);
        store.observeElders(entry, 4); assertEquals(4, entry.elders);
        store.update(entry, 12, 2, 4);
        assertEquals(2, entry.elders); assertEquals(12, entry.sponges);
    }
    @Test void retainedZeroDoesNotCompleteANewSpongeMilestoneWithoutCoverage() throws Exception {
        var store = new MonumentStore(folder, "world", "minecraft:overworld");
        var bounds = new MonumentBounds(0, 0); store.discover(bounds, false, 1);
        var entry = store.get(bounds); store.update(entry, 24, 0, 2);
        store.update(entry, 0, -1, 3);
        assertEquals(0, entry.elders); assertEquals(NO_SPONGES, entry.state());
        store.update(entry, 0, 0, 4); assertEquals(CLEARED, entry.state());
    }
    @Test void zeroEldersClearsAnAlreadyEmptyMonumentWithoutDeathHistory() throws Exception {
        var store = new MonumentStore(folder, "world", "minecraft:overworld");
        var b = new MonumentBounds(0, 0);
        assertTrue(store.discover(b, false, 1));
        var e = store.get(b);
        assertEquals(DISCOVERED, e.state());
        store.update(e, 0, 0, 2);
        assertEquals(CLEARED, e.state());
        assertEquals("Monument: OK (completed)", e.label());
    }
    @Test void milestonesOnlyAdvanceWhenSpongesOrEldersReappear() throws Exception {
        var store = new MonumentStore(folder, "world", "minecraft:overworld");
        var b = new MonumentBounds(0, 0); store.discover(b, false, 1);
        var e = store.get(b);
        store.update(e, 24, 3, 2); assertEquals(SPONGES_REMAIN, e.state());
        store.update(e, 0, 1, 3); assertEquals(NO_SPONGES, e.state());
        store.update(e, 20, 3, 4); assertEquals(NO_SPONGES, e.state());
        store.update(e, 20, -1, 5); assertEquals(NO_SPONGES, e.state());
        // The sponge milestone is already earned; later zero elders advances to OK.
        store.update(e, 20, 0, 6); assertEquals(CLEARED, e.state());
        String completedLabel = e.label();
        store.observeElders(e, 3); assertEquals(CLEARED, e.state());
        store.update(e, 40, -1, 7); assertEquals(CLEARED, e.state());
        e.fresh = false;
        assertEquals(completedLabel, e.label());
        assertTrue(e.everHadSponges);
    }
    @Test void unknownEldersDoNotClearMonument() throws Exception {
        var store = new MonumentStore(folder, "world", "minecraft:overworld");
        var b = new MonumentBounds(0, 0); store.discover(b, false, 1);
        store.update(store.get(b), 0, -1, 2);
        assertEquals(NO_SPONGES, store.get(b).state());
        assertTrue(store.get(b).label().contains("elders: ?"));
    }
    @Test void deduplicatesAndPersistsCompletedMilestoneDespiteLaterObservations() throws Exception {
        var b = new MonumentBounds(-32, -48);
        var store = new MonumentStore(folder, "world", "minecraft:overworld");
        assertTrue(store.discover(b, true, 1));
        assertFalse(store.discover(b, false, 2));
        store.update(store.get(b), 0, 0, 3);
        store.update(store.get(b), 20, 3, 4);
        store.save();
        var reloaded = new MonumentStore(folder, "world", "minecraft:overworld");
        assertEquals(1, reloaded.entries().size());
        assertTrue(reloaded.get(b).manual);
        assertFalse(reloaded.get(b).fresh);
        assertEquals(CLEARED, reloaded.get(b).state());
        assertEquals("Monument: OK (completed)", reloaded.get(b).label());
        reloaded.update(reloaded.get(b), 10, -1, 5);
        assertEquals(CLEARED, reloaded.get(b).state());
    }
    @Test void spongeMilestonePersistsAndCanAdvanceAfterRestart() throws Exception {
        var b = new MonumentBounds(0, 0);
        var store = new MonumentStore(folder, "world", "minecraft:overworld");
        store.discover(b, false, 1);
        store.update(store.get(b), 0, 3, 2);
        store.update(store.get(b), 10, -1, 3);
        store.save();
        var reloaded = new MonumentStore(folder, "world", "minecraft:overworld");
        assertEquals(NO_SPONGES, reloaded.get(b).state());
        assertTrue(reloaded.get(b).label().contains("sponges cleared"));
        assertFalse(reloaded.get(b).label().contains("last survey"));
        reloaded.update(reloaded.get(b), 10, 0, 4);
        assertEquals(CLEARED, reloaded.get(b).state());
    }
    @Test void legacyNoSpongesAndClearedRecordsMigrateToPermanentMilestones() throws Exception {
        var path = folder.resolve(MonumentStore.key("world\nminecraft:overworld") + ".json");
        Files.writeString(path, """
            {"schema":1,"monuments":{
              "0,0":{"centerX":0,"centerZ":0,"sponges":0,"elders":3},
              "128,0":{"centerX":128,"centerZ":0,"sponges":0,"elders":0},
              "256,0":{"centerX":256,"centerZ":0,"sponges":20,"elders":0}
            }}
            """);
        var store = new MonumentStore(folder, "world", "minecraft:overworld");
        var yellow = store.get(new MonumentBounds(0, 0));
        var green = store.get(new MonumentBounds(128, 0));
        assertEquals(NO_SPONGES, yellow.state());
        assertEquals(CLEARED, green.state());
        assertEquals(SPONGES_REMAIN, store.get(new MonumentBounds(256, 0)).state());
        store.update(yellow, 20, 3, 2);
        store.update(green, 20, 3, 2);
        store.save();
        var reloaded = new MonumentStore(folder, "world", "minecraft:overworld");
        assertEquals(NO_SPONGES, reloaded.get(yellow.bounds()).state());
        assertEquals(CLEARED, reloaded.get(green.bounds()).state());
    }
    @Test void seeingZeroEldersBeforeAnySpongeClearanceDoesNotCompleteIt() throws Exception {
        var store = new MonumentStore(folder, "world", "minecraft:overworld");
        var b = new MonumentBounds(0, 0); store.discover(b, false, 1);
        store.update(store.get(b), 10, 0, 2);
        assertEquals(SPONGES_REMAIN, store.get(b).state());
        store.update(store.get(b), 0, -1, 3);
        assertEquals(NO_SPONGES, store.get(b).state());
    }
    @Test void savesAreSeparatedByWorldAndDimension() throws Exception {
        var a = new MonumentStore(folder, "a", "minecraft:overworld");
        a.discover(new MonumentBounds(0, 0), false, 1); a.save();
        assertTrue(new MonumentStore(folder, "b", "minecraft:overworld").entries().isEmpty());
        assertTrue(new MonumentStore(folder, "a", "minecraft:the_nether").entries().isEmpty());
    }
    @Test void malformedDataIsPreserved() throws Exception {
        var path = folder.resolve(MonumentStore.key("world\nminecraft:overworld") + ".json");
        Files.writeString(path, "{bad");
        assertThrows(IOException.class, () -> new MonumentStore(folder, "world", "minecraft:overworld"));
        assertEquals("{bad", Files.readString(path));
    }
}
