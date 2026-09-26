package dev.frydae.slime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class SightingStoreTest {
    @TempDir Path folder;
    @Test void persistsAndUpgradesEvidenceWithoutDuplicatingChunk() throws Exception {
        var store = new SightingStore(folder, "server:example", "minecraft:overworld");
        assertTrue(store.record(-2, -1, 65, false, 100));
        assertFalse(store.record(-2, -1, 20, true, 200));
        store.record(-2, -1, 70, false, 300);
        store.save();
        var loaded = new SightingStore(folder, "server:example", "minecraft:overworld");
        assertEquals(1, loaded.entries().size());
        var e = loaded.get(-2, -1);
        assertTrue(e.underground);
        assertEquals(20, e.minY);
        assertEquals(3, e.observations);
        assertEquals(1, e.visits);
        assertEquals(100, e.firstSeen);
        assertEquals(300, e.lastSeen);
    }
    @Test void worldsAndDimensionsAreIsolated() throws Exception {
        var a = new SightingStore(folder, "server:a", "minecraft:overworld");
        a.record(1, 2, 10, true, 1); a.save();
        assertTrue(new SightingStore(folder, "server:b", "minecraft:overworld").entries().isEmpty());
        assertTrue(new SightingStore(folder, "server:a", "minecraft:the_nether").entries().isEmpty());
    }
    @Test void corruptDataIsNotOverwritten() throws Exception {
        var path = folder.resolve(SightingStore.key("world\nminecraft:overworld") + ".json");
        Files.writeString(path, "broken json");
        assertThrows(IOException.class, () -> new SightingStore(folder, "world", "minecraft:overworld"));
        assertEquals("broken json", Files.readString(path));
    }
    @Test void movedSightingsDoNotBecomeUndergroundCandidates() throws Exception {
        var store = new SightingStore(folder, "world", "minecraft:overworld");
        store.record(0, 0, 12, false, 1);
        assertFalse(store.get(0, 0).underground);
    }
    @Test void manySlimesCountOnceUntilChunkUnloads() throws Exception {
        var store = new SightingStore(folder, "world", "minecraft:overworld");
        for (int i = 0; i < 100; i++) store.record(-1, 2, 20, true, i);
        assertEquals(1, store.entries().size());
        assertEquals(1, store.get(-1, 2).visits);
        store.unload(9, 9);
        store.record(-1, 2, 20, true, 101);
        assertEquals(1, store.get(-1, 2).visits);
        store.unload(-1, 2);
        assertEquals(1, store.get(-1, 2).visits); // Loading alone isn't a positive visit.
        store.record(-1, 2, 20, true, 102);
        store.record(-1, 2, 20, true, 103);
        assertEquals(2, store.get(-1, 2).visits);
        store.save();
        var reconnected = new SightingStore(folder, "world", "minecraft:overworld");
        assertEquals(2, reconnected.get(-1, 2).visits);
        reconnected.record(-1, 2, 20, true, 104);
        assertEquals(3, reconnected.get(-1, 2).visits);
    }
    @Test void legacyEntityCountsAreNotMisrepresentedAsVisits() throws Exception {
        var path = folder.resolve(SightingStore.key("world\nminecraft:overworld") + ".json");
        Files.writeString(path, """
            {"schema":1,"chunks":{"0,0":{"x":0,"z":0,"minY":20,
            "observations":85,"firstSeen":1,"lastSeen":9,"underground":true}}}
            """);
        var store = new SightingStore(folder, "world", "minecraft:overworld");
        assertEquals(1, store.get(0, 0).visits);
        assertEquals(85, store.get(0, 0).observations);
        store.record(0, 0, 20, true, 10);
        store.save();
        assertEquals(2, new SightingStore(folder, "world", "minecraft:overworld").get(0, 0).visits);
    }
    @Test void deletedChunkNeverReappearsOrAccumulatesNewSightingsUntilRestored() throws Exception {
        var store = new SightingStore(folder, "world", "minecraft:overworld");
        store.record(1, 2, 20, true, 1);
        assertTrue(store.dismiss("1,2"));
        store.unload(1, 2);
        assertFalse(store.record(1, 2, 15, true, 2));
        assertEquals(1, store.get(1, 2).visits);
        store.save();
        var loaded = new SightingStore(folder, "world", "minecraft:overworld");
        assertTrue(loaded.isDismissed("1,2"));
        loaded.record(1, 2, 10, true, 3);
        assertTrue(loaded.get(1, 2).dismissed);
        assertEquals(1, loaded.get(1, 2).observations);
        assertTrue(loaded.restore(1, 2));
        loaded.record(1, 2, 20, true, 4);
        assertFalse(loaded.isDismissed("1,2"));
        assertEquals(2, loaded.get(1, 2).visits);
    }
    @Test void splitChildIdentitySurvivesRestartAndIsWorldScoped() throws Exception {
        var id = java.util.UUID.randomUUID();
        var store = new SightingStore(folder, "world", "minecraft:overworld");
        store.ignoreSplitChild(id); store.save();
        assertTrue(new SightingStore(folder, "world", "minecraft:overworld").isSplitChild(id));
        assertFalse(new SightingStore(folder, "other", "minecraft:overworld").isSplitChild(id));
    }
    @Test void schemaTwoLoadsWithoutSuppressingExistingRecords() throws Exception {
        var path = folder.resolve(SightingStore.key("world\nminecraft:overworld") + ".json");
        Files.writeString(path, """
            {"schema":2,"chunks":{"0,0":{"x":0,"z":0,"minY":20,
            "observations":5,"visits":3,"firstSeen":1,"lastSeen":9,"underground":true}}}
            """);
        var store = new SightingStore(folder, "world", "minecraft:overworld");
        assertFalse(store.get(0, 0).dismissed);
        assertEquals(3, store.get(0, 0).visits);
        assertFalse(store.isSplitChild(java.util.UUID.randomUUID()));
    }
    @Test void deadSplitChildrenAreRemovedFromPersistentIgnoreList() throws Exception {
        var id = java.util.UUID.randomUUID();
        var store = new SightingStore(folder, "world", "minecraft:overworld");
        store.ignoreSplitChild(id); store.save();
        store.forgetDeadSplitChild(id); store.save();
        assertFalse(new SightingStore(folder, "world", "minecraft:overworld").isSplitChild(id));
    }
}
