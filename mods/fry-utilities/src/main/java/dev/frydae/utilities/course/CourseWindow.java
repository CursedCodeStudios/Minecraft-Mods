package dev.frydae.utilities.course;

import java.util.ArrayList;
import java.util.List;

/** Clips a directional course to the next measured distance along its segments. */
public final class CourseWindow {
    public record Point(double x, double y, double z) {}

    private CourseWindow() {}

    public static List<Point> ahead(List<CourseGraph.Point> course, CourseGraph.Point player,
        double distance) {
        if (course.size() < 2 || distance <= 0) return List.of();

        int closestSegment = -1;
        double closestFraction = 0;
        double closestDistanceSquared = Double.POSITIVE_INFINITY;
        for (int i = 0; i < course.size() - 1; i++) {
            var a = course.get(i);
            var b = course.get(i + 1);
            double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
            double lengthSquared = dx * dx + dy * dy + dz * dz;
            if (lengthSquared == 0) continue;
            double fraction = Math.clamp(((player.x() - a.x()) * dx
                + (player.y() - a.y()) * dy + (player.z() - a.z()) * dz) / lengthSquared, 0, 1);
            double px = a.x() + fraction * dx - player.x();
            double py = a.y() + fraction * dy - player.y();
            double pz = a.z() + fraction * dz - player.z();
            double distanceSquared = px * px + py * py + pz * pz;
            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closestSegment = i;
                closestFraction = fraction;
            }
        }
        if (closestSegment < 0) return List.of();

        var start = course.get(closestSegment);
        var end = course.get(closestSegment + 1);
        Point current = new Point(
            start.x() + closestFraction * (end.x() - start.x()),
            start.y() + closestFraction * (end.y() - start.y()),
            start.z() + closestFraction * (end.z() - start.z()));
        var window = new ArrayList<Point>();
        window.add(current);
        double remaining = distance;
        for (int i = closestSegment; i < course.size() - 1 && remaining > 0; i++) {
            var next = course.get(i + 1);
            double dx = next.x() - current.x(), dy = next.y() - current.y(), dz = next.z() - current.z();
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length == 0) continue;
            if (length <= remaining) {
                current = new Point(next.x(), next.y(), next.z());
                window.add(current);
                remaining -= length;
            } else {
                double fraction = remaining / length;
                window.add(new Point(current.x() + fraction * dx,
                    current.y() + fraction * dy, current.z() + fraction * dz));
                break;
            }
        }
        return List.copyOf(window);
    }
}
