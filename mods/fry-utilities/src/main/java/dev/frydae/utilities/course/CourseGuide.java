package dev.frydae.utilities.course;

import java.util.List;

/** A recorded direction takes priority; the opposite track can guide an unrecorded return. */
public final class CourseGuide {
    private CourseGuide() {}

    public static List<CourseGraph.Point> points(CourseGraph.Course forward,
        CourseGraph.Course reverse, boolean fromA) {
        CourseGraph.Course measured = fromA ? forward : reverse;
        if (measured != null) return measured.points();
        CourseGraph.Course opposite = fromA ? reverse : forward;
        return opposite == null ? List.of() : opposite.points().reversed();
    }
}
