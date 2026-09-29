package dev.frydae.utilities.course;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CourseGraphTest {
    private static CourseGraph.Point p(double x, double y, double z, long tick) {
        return new CourseGraph.Point(x, y, z, tick);
    }

    @Test void combinesFastSectionsOfDifferentFlightsAtObservedJunction() {
        var fastStart = new CourseGraph.Flight(List.of(
            p(0, 64, 0, 1), p(4.1, 64, 0.1, 21), p(10, 64, 0, 101)));
        var fastFinish = new CourseGraph.Flight(List.of(
            p(0, 64, 0, 1), p(4.2, 64, 0.2, 61), p(10, 64, 0, 81)));

        var course = CourseGraph.fastest(List.of(fastStart, fastFinish));

        assertNotNull(course);
        assertEquals(41, course.estimatedTicks());
        assertEquals(4, course.points().size());
        assertEquals(4.1, course.points().get(1).x());
        assertEquals(4.2, course.points().get(2).x());
    }

    @Test void doesNotShortcutBetweenDistantOrOpposingTracks() {
        var one = new CourseGraph.Flight(List.of(
            p(0, 64, 0, 1), p(4.1, 64, 0.1, 21), p(10, 64, 0, 101)));
        var offset = new CourseGraph.Flight(List.of(
            p(0, 65.5, 0, 1), p(4.2, 65.5, 0.2, 61), p(10, 65.5, 0, 81)));
        var opposing = new CourseGraph.Flight(List.of(
            p(10, 64, 0, 1), p(4.2, 64, 0.2, 11), p(0, 64, 0, 71)));

        assertEquals(70, CourseGraph.fastest(List.of(one, opposing)).estimatedTicks());
        assertEquals(80, CourseGraph.fastest(List.of(one, offset)).estimatedTicks());
    }
}
