package dev.frydae.utilities.course;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CourseGuideTest {
    private static CourseGraph.Point p(double x, long tick) {
        return new CourseGraph.Point(x, 70, 0, tick);
    }

    @Test void usesRecordedTrackBackwardsUntilReturnFlightIsMeasured() {
        var outward = new CourseGraph.Course(List.of(p(0, 1), p(40, 21), p(100, 61)), 60);
        var returnTrip = new CourseGraph.Course(List.of(p(100, 1), p(55, 31), p(0, 71)), 70);

        assertEquals(List.of(100.0, 40.0, 0.0), CourseGuide.points(outward, null, false)
            .stream().map(CourseGraph.Point::x).toList());
        assertEquals(List.of(100.0, 55.0, 0.0), CourseGuide.points(outward, returnTrip, false)
            .stream().map(CourseGraph.Point::x).toList());
        assertEquals(List.of(0.0, 55.0, 100.0), CourseGuide.points(null, returnTrip, true)
            .stream().map(CourseGraph.Point::x).toList());
        assertTrue(CourseGuide.points(null, null, false).isEmpty());
    }
}
