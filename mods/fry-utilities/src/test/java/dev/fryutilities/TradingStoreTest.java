package dev.fryutilities;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TradingStoreTest {
    @TempDir Path folder;
    private static final UUID VILLAGER = UUID.fromString("67a68110-3b1b-4e20-bcf8-8bf4298d0845");

    private TradingStore store() throws Exception { return new TradingStore(folder, "server", "minecraft:overworld"); }

    @Test void togglesAndPersistsFavoritesPerVillager() throws Exception {
        var store = store();
        assertTrue(store.toggle(VILLAGER, 3, true));
        assertTrue(store.isFavorite(VILLAGER, 3));
        assertTrue(store.hasAvailableFavorite(VILLAGER));
        store.save();
        var loaded = store();
        assertTrue(loaded.isFavorite(VILLAGER, 3));
        assertTrue(loaded.hasAvailableFavorite(VILLAGER));
        assertFalse(loaded.toggle(VILLAGER, 3, false));
        assertEquals(0, loaded.favoriteCount());
    }

    @Test void corruptDataIsPreserved() throws Exception {
        Path file = folder.resolve(TradingStore.hash("server\nminecraft:overworld") + ".json");
        Files.writeString(file, "{bad");
        assertThrows(java.io.IOException.class, this::store);
        assertEquals("{bad", Files.readString(file));
    }

    @Test void estimatesAtMostTwoRestocksPerPeriodWithVanillaCooldown() throws Exception {
        var store = store();
        store.toggle(VILLAGER, 0, false);
        assertTrue(store.markProbablyRestocked(VILLAGER, 10, 100));
        assertTrue(store.hasAvailableFavorite(VILLAGER));
        assertEquals(1, store.estimatedVillagerCount());
        store.save();
        store = store();
        assertEquals(1, store.estimatedVillagerCount());

        store.toggle(VILLAGER, 1, false);
        assertFalse(store.markProbablyRestocked(VILLAGER, 10, 2500));
        assertTrue(store.markProbablyRestocked(VILLAGER, 10, 2501));

        store.toggle(VILLAGER, 2, false);
        assertFalse(store.markProbablyRestocked(VILLAGER, 10, 5002));
        assertTrue(store.markProbablyRestocked(VILLAGER, 11, 5002));
    }
}
