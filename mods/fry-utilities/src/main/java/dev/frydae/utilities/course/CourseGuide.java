package dev.frydae.utilities.course;

import java.util.List;

/** Uses the quicker observed track as the guide in either direction. */
public final class CourseGuide {
    public record Selection(List<CourseGraph.Point> points, boolean fromOpposite) {}

    private CourseGuide() {}

    public static Selection select(CourseGraph.Course forward,
        CourseGraph.Course reverse, boolean fromA) {
        CourseGraph.Course measured = fromA ? forward : reverse;
        CourseGraph.Course opposite = fromA ? reverse : forward;
        if (opposite != null && (measured == null
            || opposite.estimatedTicks() < measured.estimatedTicks()))
            return new Selection(opposite.points().reversed(), true);
        return new Selection(measured == null ? List.of() : measured.points(), false);
    }
}
