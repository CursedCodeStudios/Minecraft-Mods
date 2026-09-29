package dev.frydae.utilities.course;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Selects a course only while the player is within five blocks of an endpoint. */
public final class CourseProximity {
    public record Match(String name, boolean fromA, double distanceSquared) {}
    private static final double RADIUS_SQUARED = 25;
    private static final double LAUNCH_RADIUS_SQUARED = 24 * 24;
    private static final long LAUNCH_WINDOW_TICKS = 100;

    private CourseProximity() {}

    public static List<Match> nearby(Collection<CourseStore.Course> courses, String preferred,
        CourseGraph.Point position) {
        var matches = new ArrayList<Match>();
        for (var course : courses) {
            if (course.a() != null) add(matches, course.name(), true, course.a().distanceSquared(position));
            if (course.b() != null) add(matches, course.name(), false, course.b().distanceSquared(position));
        }
        matches.sort(Comparator.comparingInt((Match m) -> m.name().equalsIgnoreCase(preferred) ? 0 : 1)
            .thenComparingDouble(Match::distanceSquared).thenComparing(Match::name));
        return List.copyOf(matches);
    }

    private static void add(List<Match> matches, String name, boolean fromA, double distanceSquared) {
        if (distanceSquared <= RADIUS_SQUARED) matches.add(new Match(name, fromA, distanceSquared));
    }

    public static boolean canLaunchFrom(CourseStore.Anchor start, CourseGraph.Point position,
        long ticksSinceEndpoint) {
        return start != null && ticksSinceEndpoint >= 0 && ticksSinceEndpoint <= LAUNCH_WINDOW_TICKS
            && start.distanceSquared(position) <= LAUNCH_RADIUS_SQUARED;
    }
}
