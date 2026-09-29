package dev.frydae.utilities.course;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CourseStoreTest {
    @TempDir Path folder;

    @Test void persistsWorldSpecificRoutesAndClearsFlightsWhenEndpointMoves() throws Exception {
        var store = new CourseStore(folder, "server:nether.example");
        store.setAnchor(true, new CourseStore.Anchor(0, 70, 0));
        store.setAnchor(false, new CourseStore.Anchor(100, 70, 0));
        store.addFlight(true, new CourseGraph.Flight(List.of(
            new CourseGraph.Point(0, 70, 0, 1), new CourseGraph.Point(100, 70, 0, 101))));

        var reopened = new CourseStore(folder, "server:nether.example");
        assertEquals(1, reopened.flights(true).size());
        assertEquals(0, new CourseStore(folder, "server:other.example").flights(true).size());
        reopened.setAnchor(false, new CourseStore.Anchor(120, 70, 0));
        assertTrue(new CourseStore(folder, "server:nether.example").flights(true).isEmpty());
    }
}
