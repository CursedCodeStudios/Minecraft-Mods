package dev.frydae.utilities;

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
        assertFalse(store.hasAvailableFavorite(VILLAGER));
        assertTrue(store.toggleHighlight(VILLAGER, 3));
        assertTrue(store.isHighlightFavorite(VILLAGER, 3));
        assertTrue(store.hasAvailableFavorite(VILLAGER));
        store.save();
        var loaded = store();
        assertTrue(loaded.isFavorite(VILLAGER, 3));
        assertTrue(loaded.isHighlightFavorite(VILLAGER, 3));
        assertTrue(loaded.hasAvailableFavorite(VILLAGER));
        assertFalse(loaded.toggle(VILLAGER, 3, false));
        assertFalse(loaded.isHighlightFavorite(VILLAGER, 3));
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
        store.toggleHighlight(VILLAGER, 0);
        assertTrue(store.markProbablyRestocked(VILLAGER, 10, 100));
        assertTrue(store.hasAvailableFavorite(VILLAGER));
        assertEquals(1, store.estimatedVillagerCount());
        store.save();
        store = store();
        assertEquals(1, store.estimatedVillagerCount());

        store.toggle(VILLAGER, 1, false);
        store.toggleHighlight(VILLAGER, 1);
        assertFalse(store.markProbablyRestocked(VILLAGER, 10, 2500));
        assertTrue(store.markProbablyRestocked(VILLAGER, 10, 2501));

        store.toggle(VILLAGER, 2, false);
        store.toggleHighlight(VILLAGER, 2);
        assertFalse(store.markProbablyRestocked(VILLAGER, 10, 5002));
        assertTrue(store.markProbablyRestocked(VILLAGER, 11, 5002));
    }

    @Test void legacyStarsDoNotAutomaticallyBecomeHighlightTargets() throws Exception {
        Path file = folder.resolve(TradingStore.hash("server\nminecraft:overworld") + ".json");
        Files.writeString(file, """
            {"schema":1,"villagers":{"%s":{"favorites":[0],"available":{"0":true},
            "estimatedAvailable":[0],"bookLabels":{},"restockPeriod":0,"estimatedRestocks":0,
            "lastEstimatedRestock":-9223372036854775808}}}
            """.formatted(VILLAGER));
        var loaded = store();
        assertTrue(loaded.isFavorite(VILLAGER, 0));
        assertFalse(loaded.isHighlightFavorite(VILLAGER, 0));
        assertFalse(loaded.hasAvailableFavorite(VILLAGER));
        assertEquals(0, loaded.estimatedVillagerCount());
    }
}
