package dev.frydae.utilities.course;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CourseGuideTest {
    private static CourseGraph.Point p(double x, long tick) {
        return new CourseGraph.Point(x, 70, 0, tick);
    }

    @Test void usesFasterOutwardTrackForTheRecordedReturnDirection() {
        var outward = new CourseGraph.Course(List.of(p(0, 1), p(40, 21), p(100, 61)), 60);
        var returnTrip = new CourseGraph.Course(List.of(p(100, 1), p(55, 31), p(0, 71)), 70);

        var selected = CourseGuide.select(outward, returnTrip, false);
        assertEquals(List.of(100.0, 40.0, 0.0),
            selected.points().stream().map(CourseGraph.Point::x).toList());
        assertTrue(selected.fromOpposite());
    }

    @Test void usesFasterReturnTrackForOutwardDirectionAndKeepsMeasuredRouteOnTie() {
        var outward = new CourseGraph.Course(List.of(p(0, 1), p(40, 21), p(100, 61)), 60);
        var returnTrip = new CourseGraph.Course(List.of(p(100, 1), p(55, 31), p(0, 51)), 50);

        assertEquals(List.of(0.0, 55.0, 100.0), CourseGuide.select(outward, returnTrip, true)
            .points().stream().map(CourseGraph.Point::x).toList());
        assertEquals(List.of(100.0, 55.0, 0.0), CourseGuide.select(outward, returnTrip, false)
            .points().stream().map(CourseGraph.Point::x).toList());

        var tiedReturn = new CourseGraph.Course(returnTrip.points(), 60);
        assertFalse(CourseGuide.select(outward, tiedReturn, true).fromOpposite());
        assertFalse(CourseGuide.select(outward, tiedReturn, false).fromOpposite());
    }

    @Test void borrowsOnlyAvailableTrackAndHandlesNoFlights() {
        var outward = new CourseGraph.Course(List.of(p(0, 1), p(40, 21), p(100, 61)), 60);
        var returnTrip = new CourseGraph.Course(List.of(p(100, 1), p(55, 31), p(0, 71)), 70);

        assertEquals(List.of(100.0, 40.0, 0.0), CourseGuide.select(outward, null, false)
            .points().stream().map(CourseGraph.Point::x).toList());
        assertEquals(List.of(0.0, 55.0, 100.0), CourseGuide.select(null, returnTrip, true)
            .points().stream().map(CourseGraph.Point::x).toList());
        assertTrue(CourseGuide.select(null, null, false).points().isEmpty());
    }
}
