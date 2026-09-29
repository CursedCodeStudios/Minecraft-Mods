package dev.frydae.utilities.course;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CourseWindowTest {
    private static CourseGraph.Point at(double x, double y, double z) {
        return new CourseGraph.Point(x, y, z, 0);
    }

    @Test void startsAtNearestProjectionAndStopsTwentyBlocksAlongABend() {
        var route = List.of(at(0, 0, 0), at(10, 0, 0), at(10, 0, 30));

        assertEquals(List.of(
            new CourseWindow.Point(8, 0, 0),
            new CourseWindow.Point(10, 0, 0),
            new CourseWindow.Point(10, 0, 18)),
            CourseWindow.ahead(route, at(8, 3, 0), 20));
    }

    @Test void followsReverseCourseDirectionWithoutShowingThePathBehindPlayer() {
        var route = List.of(at(40, 0, 0), at(10, 0, 0), at(0, 0, 0));

        assertEquals(List.of(
            new CourseWindow.Point(22, 0, 0),
            new CourseWindow.Point(10, 0, 0),
            new CourseWindow.Point(2, 0, 0)),
            CourseWindow.ahead(route, at(22, 0, 0), 20));
    }

    @Test void stopsAtEndAndSkipsZeroLengthSegments() {
        var route = List.of(at(0, 0, 0), at(0, 0, 0), at(10, 0, 0));

        assertEquals(List.of(
            new CourseWindow.Point(8, 0, 0),
            new CourseWindow.Point(10, 0, 0)),
            CourseWindow.ahead(route, at(8, 0, 0), 20));
        assertTrue(CourseWindow.ahead(List.of(at(0, 0, 0)), at(0, 0, 0), 20).isEmpty());
    }
}
