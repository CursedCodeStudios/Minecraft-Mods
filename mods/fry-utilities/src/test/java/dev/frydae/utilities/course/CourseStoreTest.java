package dev.frydae.utilities.course;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CourseStoreTest {
    @TempDir Path folder;

    @Test void persistsWorldSpecificRoutesAndClearsFlightsWhenEndpointMoves() throws Exception {
        var store = new CourseStore(folder, "server:nether.example");
        store.setAnchor(true, new CourseStore.Anchor(0, 70, 0));
        store.setAnchor(false, new CourseStore.Anchor(100, 70, 0));
        store.addFlight("Default", true, new CourseGraph.Flight(List.of(
            new CourseGraph.Point(0, 70, 0, 1), new CourseGraph.Point(100, 70, 0, 101))));

        var reopened = new CourseStore(folder, "server:nether.example");
        assertEquals(1, reopened.selected().flights(true).size());
        assertEquals(0, new CourseStore(folder, "server:other.example").selected().flights(true).size());
        reopened.setAnchor(false, new CourseStore.Anchor(120, 70, 0));
        assertTrue(new CourseStore(folder, "server:nether.example").selected().flights(true).isEmpty());
    }

    @Test void namedCoursesStaySeparateAndNamesAreUniqueIgnoringCase() throws Exception {
        var store = new CourseStore(folder, "server:nether.example");
        store.setAnchor(true, new CourseStore.Anchor(0, 70, 0));
        store.setAnchor(false, new CourseStore.Anchor(100, 70, 0));
        assertTrue(store.rename("Default", "Hub to Farm"));
        assertFalse(store.create("hub TO farm"));
        assertTrue(store.renameEndpoint(true, "Nether Hub"));
        assertTrue(store.renameEndpoint(false, "Gold Farm"));
        assertFalse(store.renameEndpoint(false, "nether hub"));
        assertTrue(store.create("Fortress Route"));
        store.setAnchor(true, new CourseStore.Anchor(200, 70, 0));
        store.setAnchor(false, new CourseStore.Anchor(300, 70, 0));
        assertTrue(store.select("HUB TO FARM"));

        var reopened = new CourseStore(folder, "server:nether.example");
        assertEquals("Hub to Farm", reopened.selected().name());
        assertEquals("Nether Hub", reopened.selected().aName());
        assertEquals(0, reopened.selected().a().x());
        assertEquals(200, reopened.get("fortress route").a().x());
        assertEquals(2, reopened.names().size());
        assertTrue(reopened.delete("Fortress Route"));
        assertFalse(reopened.delete("Hub to Farm"));
    }

    @Test void upgradesOriginalSingleCourseWithoutLosingFlights() throws Exception {
        var store = new CourseStore(folder, "server:nether.example");
        store.setAnchor(true, new CourseStore.Anchor(0, 70, 0));
        Path file;
        try (var files = Files.list(folder)) { file = files.findFirst().orElseThrow(); }
        Files.writeString(file, """
            {"schema":1,"a":{"x":0,"y":70,"z":0},"b":{"x":100,"y":70,"z":0},
             "forward":[{"points":[{"x":0,"y":70,"z":0,"tick":1},
                                    {"x":100,"y":70,"z":0,"tick":101}]}],"reverse":[]}
            """);

        var migrated = new CourseStore(folder, "server:nether.example");
        assertEquals("Default", migrated.selected().name());
        assertEquals(1, migrated.selected().flights(true).size());
        assertTrue(migrated.rename("Default", "Old Highway"));
        assertEquals(2, JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("schema").getAsInt());
        assertEquals(1, new CourseStore(folder, "server:nether.example")
            .get("Old Highway").flights(true).size());
    }
}
