package dev.bedrockscout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockStoreTest {
    @TempDir Path folder;
    private BedrockStore store() throws Exception { return new BedrockStore(folder, "world", "minecraft:the_nether"); }

    @Test void keepsOneLowestWaypointForEachHorizontalCenter() throws Exception {
        var store = store();
        assertNotNull(store.discover(-20, 126, 30, 1));
        assertNull(store.discover(-20, 127, 30, 2));
        assertNotNull(store.discover(-20, 124, 30, 3));
        assertEquals(1, store.entries().size());
        assertEquals(124, store.entries().iterator().next().y);
    }

    @Test void persistsSeparatelyByWorldAndDimension() throws Exception {
        var store = store(); store.discover(8, 125, -8, 1); store.save();
        var loaded = store();
        assertEquals(1, loaded.entries().size());
        assertEquals("Nether Roof 3x3 (Y=125)", loaded.entries().iterator().next().label());
        assertTrue(new BedrockStore(folder, "other", "minecraft:the_nether").entries().isEmpty());
        assertTrue(new BedrockStore(folder, "world", "minecraft:overworld").entries().isEmpty());
    }

    @Test void corruptDataIsPreserved() throws Exception {
        var path = folder.resolve(BedrockStore.hash("world\nminecraft:the_nether") + ".json");
        Files.writeString(path, "{bad");
        assertThrows(IOException.class, this::store);
        assertEquals("{bad", Files.readString(path));
    }
}
