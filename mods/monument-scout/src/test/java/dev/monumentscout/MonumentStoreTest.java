package dev.monumentscout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static dev.monumentscout.MonumentStore.State.*;

class MonumentStoreTest {
    @TempDir Path folder;
    @Test void zeroEldersClearsAnAlreadyEmptyMonumentWithoutDeathHistory() throws Exception {
        var store = new MonumentStore(folder, "world", "minecraft:overworld");
        var b = new MonumentBounds(0, 0);
        assertTrue(store.discover(b, false, 1));
        var e = store.get(b);
        assertEquals(DISCOVERED, e.state());
        store.update(e, 0, 0, 2);
        assertEquals(CLEARED, e.state());
        assertTrue(e.label().contains("elders: 0"));
    }
    @Test void followsBothLootingStagesAndReversesWhenThingsReappear() throws Exception {
        var store = new MonumentStore(folder, "world", "minecraft:overworld");
        var b = new MonumentBounds(0, 0); store.discover(b, false, 1);
        var e = store.get(b);
        store.update(e, 24, 3, 2); assertEquals(SPONGES_REMAIN, e.state());
        store.update(e, 0, 1, 3); assertEquals(NO_SPONGES, e.state());
        store.update(e, 0, 0, 4); assertEquals(CLEARED, e.state());
        store.observeElders(e, 1); assertEquals(NO_SPONGES, e.state());
        store.update(e, 1, 0, 5); assertEquals(SPONGES_REMAIN, e.state());
        assertTrue(e.everHadSponges);
    }
    @Test void unknownEldersDoNotClearMonument() throws Exception {
        var store = new MonumentStore(folder, "world", "minecraft:overworld");
        var b = new MonumentBounds(0, 0); store.discover(b, false, 1);
        store.update(store.get(b), 0, -1, 2);
        assertEquals(NO_SPONGES, store.get(b).state());
        assertTrue(store.get(b).label().contains("elders: ?"));
    }
    @Test void deduplicatesAndPersistsHistoricalStatusWithoutClaimingItIsLive() throws Exception {
        var b = new MonumentBounds(-32, -48);
        var store = new MonumentStore(folder, "world", "minecraft:overworld");
        assertTrue(store.discover(b, true, 1));
        assertFalse(store.discover(b, false, 2));
        store.update(store.get(b), 0, 0, 3);
        store.save();
        var reloaded = new MonumentStore(folder, "world", "minecraft:overworld");
        assertEquals(1, reloaded.entries().size());
        assertTrue(reloaded.get(b).manual);
        assertFalse(reloaded.get(b).fresh);
        assertEquals(CLEARED, reloaded.get(b).state());
        assertTrue(reloaded.get(b).label().contains("last survey"));
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
