package dev.endcityscout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import dev.endcityscout.CitySignature.Pos;
import static dev.endcityscout.CityStore.Presence.*;

class CityStoreTest {
    @TempDir Path folder;
    private CityStore store() throws Exception { return new CityStore(folder, "world", "minecraft:the_end"); }
    @Test void branchTowersShareTheLowestCenterRegardlessOfDiscoveryOrder() throws Exception {
        var base = new Pos(-100, 65, -100);
        var towers = java.util.List.of(new Pos(-165, 95, -100), new Pos(-30, 110, -100), new Pos(-150, 125, -180));
        var s = store();
        for (var tower : towers) s.discover(tower, false);
        s.discover(base, false);
        assertEquals(1, s.cities().size());
        assertEquals(base, s.cities().iterator().next().center);
        for (var tower : towers) assertEquals(base, s.owner(tower.x(), tower.z()).center);
        var other = new CityStore(folder, "reverse", "minecraft:the_end");
        other.discover(base, false);
        for (var tower : towers.reversed()) other.discover(tower, false);
        assertEquals(base, other.cities().iterator().next().center);
        assertEquals(1, other.cities().size());
    }
    @Test void savedTowerDuplicatesAreHiddenAndLegacyPartialCountsAreReset() throws Exception {
        var s = store(); var base = new Pos(0, 65, 0);
        s.discover(new Pos(64, 100, 0), false); s.discover(base, false);
        s.discoverShip(new Pos(90, 120, 0));
        var c = s.cities().iterator().next();
        s.observe(c, 0, true, 12); s.searched(c);
        s.observe(s.ships().iterator().next(), PRESENT); s.save();
        var path = folder.resolve(CityStore.key("world\nminecraft:the_end") + ".json");
        Files.writeString(path, Files.readString(path).replace("\"schema\": 2", "\"schema\": 1"));
        var loaded = store(); var center = loaded.cities().iterator().next();
        assertEquals(1, loaded.cities().size()); assertEquals(base, center.center);
        assertEquals(-1, center.shulkers); assertFalse(center.searched);
        assertEquals(1, loaded.ships(center).size());
        assertEquals(PRESENT, loaded.ships(center).getFirst().elytra);
        loaded.observe(center, 4, true, 13); loaded.save();
        assertEquals(4, store().cities().iterator().next().shulkers);
    }
    @Test void groupingDoesNotChainThroughBranchTowersIntoAnotherCity() throws Exception {
        var s = store();
        s.discover(new Pos(0, 60, 0), false);
        s.discover(new Pos(160, 100, 0), false);
        s.discover(new Pos(300, 70, 0), false);
        assertEquals(2, s.cities().size());
        assertEquals(300, s.owner(300, 0).center.x());
    }
    @Test void shipIsNotEvidenceOfElytraAndCanBeFoundBeforeItsCity() throws Exception {
        var s = store();
        s.discoverShip(new Pos(-50, 120, -80));
        assertNull(s.owner(-50, -80));
        s.discover(new Pos(-32, 60, -32), false);
        var c = s.cities().iterator().next();
        assertEquals(1, s.ships(c).size());
        assertEquals("End City (? Shulkers)", s.label(c));
        assertEquals("EC-?", s.symbol(c));
        assertFalse(s.discover(new Pos(-32, 64, -32), true));
    }
    @Test void updatesReflectLootingAndNewShulkersWithoutPermanentClearedState() throws Exception {
        var s = store(); s.discover(new Pos(0, 65, 0), false); s.discoverShip(new Pos(50, 120, 0));
        var c = s.cities().iterator().next(); var ship = s.ships().iterator().next();
        s.observe(ship, PRESENT); s.observe(c, 6, true, 1);
        assertEquals("End City (Elytra) (6 Shulkers)", s.label(c));
        assertEquals("EC-E6", s.symbol(c));
        s.observe(ship, ABSENT); s.observe(c, 0, true, 2);
        assertEquals("End City (0 Shulkers)", s.label(c));
        assertEquals("EC-0", s.symbol(c));
        s.observe(c, 2, true, 3);
        assertEquals("End City (2 Shulkers)", s.label(c));
    }
    @Test void nearestCityOwnsEachShipOnlyOnceAndFarShipsStayUnassigned() throws Exception {
        var s = store(); s.discover(new Pos(0, 65, 0), false); s.discover(new Pos(300, 65, 0), false);
        s.discoverShip(new Pos(180, 110, 0)); s.discoverShip(new Pos(1000, 110, 0));
        assertEquals(300, s.owner(180, 0).center.x());
        assertEquals(1, s.cities().stream().mapToInt(c -> s.ships(c).size()).sum());
        assertNull(s.owner(1000, 0));
    }
    @Test void persistenceRetainsResultsButNeverFreshnessAndIsolatesWorlds() throws Exception {
        var s = store(); s.discover(new Pos(-30, 60, -40), true); s.discoverShip(new Pos(0, 120, 0));
        var c = s.cities().iterator().next(); var ship = s.ships().iterator().next();
        s.searched(c); s.observe(c, 0, true, 5); s.observe(ship, ABSENT); s.save();
        var loaded = store(); var saved = loaded.cities().iterator().next();
        assertTrue(saved.manual); assertTrue(saved.searched); assertFalse(saved.fresh);
        assertFalse(loaded.ships().iterator().next().fresh);
        assertEquals("End City (0 Shulkers)", loaded.label(saved));
        assertEquals("EC-0", loaded.symbol(saved));
        assertTrue(new CityStore(folder, "another-world", "minecraft:the_end").cities().isEmpty());
        assertTrue(new CityStore(folder, "world", "minecraft:overworld").cities().isEmpty());
    }
    @Test void noShipMeansNoShipOrElytraFieldsRegardlessOfSearchFreshness() throws Exception {
        var s = store(); s.discover(new Pos(0, 65, 0), false); var c = s.cities().iterator().next();
        assertEquals("End City (? Shulkers)", s.label(c));
        s.searched(c); s.observe(c, 6, true, 10);
        assertEquals("End City (6 Shulkers)", s.label(c));
        assertEquals("EC-6", s.symbol(c));
        c.searchFresh = false;
        assertEquals("End City (6 Shulkers)", s.label(c));
        c.fresh = false;
        assertEquals("End City (6 Shulkers)", s.label(c));
    }
    @Test void corruptOrUnsupportedDataIsPreserved() throws Exception {
        var path = folder.resolve(CityStore.key("world\nminecraft:the_end") + ".json");
        for (String bad : new String[]{"{bad", "{\"schema\":99}", "{\"schema\":1,\"cities\":{\"0,0,0\":null},\"ships\":{}}"}) {
            Files.writeString(path, bad);
            assertThrows(IOException.class, this::store);
            assertEquals(bad, Files.readString(path));
        }
    }
}
