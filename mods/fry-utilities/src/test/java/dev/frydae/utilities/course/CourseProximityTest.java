package dev.frydae.utilities.course;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CourseProximityTest {
    @TempDir Path folder;

    @Test void showsEveryCourseWithinFiveBlocksAndPrefersSelectedAtSharedStart() throws Exception {
        var store = new CourseStore(folder, "local:test");
        store.setAnchor(true, new CourseStore.Anchor(0, 70, 0));
        store.setAnchor(false, new CourseStore.Anchor(100, 70, 0));
        store.create("Second");
        store.setAnchor(true, new CourseStore.Anchor(1, 70, 0));
        store.setAnchor(false, new CourseStore.Anchor(101, 70, 0));
        store.select("Default");

        var within = CourseProximity.nearby(store.courses(), store.selected().name(),
            new CourseGraph.Point(5, 70, 0, 1));
        assertEquals(2, within.size());
        assertEquals("Default", within.getFirst().name());
        assertEquals("Second", within.getLast().name());
        assertEquals(1, CourseProximity.nearby(store.courses(), "Default",
            new CourseGraph.Point(5.01, 70, 0, 1)).size());
        assertTrue(CourseProximity.nearby(store.courses(), "Default",
            new CourseGraph.Point(5, 76, 0, 1)).isEmpty());
    }

    @Test void takeoffCanStartJustOutsideVisibleEndpointRadius() {
        var start = new CourseStore.Anchor(100, 70, 0);
        var airborne = new CourseGraph.Point(100, 82, 0, 1);

        assertTrue(CourseProximity.canLaunchFrom(start, airborne, 40));
        assertFalse(CourseProximity.canLaunchFrom(start, airborne, 101));
        assertFalse(CourseProximity.canLaunchFrom(start,
            new CourseGraph.Point(100, 95, 0, 1), 40));
    }

    @Test void landingCanCompleteOutsideFiveBlockDisplayRadius() throws Exception {
        var store = new CourseStore(folder, "local:landing");
        store.setAnchor(true, new CourseStore.Anchor(0, 70, 0));
        store.setAnchor(false, new CourseStore.Anchor(100, 70, 0));
        var landing = new CourseGraph.Point(89, 70, 0, 100);

        assertTrue(CourseProximity.nearby(store.courses(), "Default", landing).isEmpty());
        assertTrue(CourseProximity.canFinishAt(store.selected().b(), landing));
        assertFalse(CourseProximity.canFinishAt(store.selected().b(),
            new CourseGraph.Point(85, 70, 0, 100)));
    }
}
