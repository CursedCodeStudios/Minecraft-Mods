package dev.frydae.utilities.course;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Finds the quickest observed route without inventing long, unflown shortcuts. */
public final class CourseGraph {
    public record Point(double x, double y, double z, long tick) {
        public double distanceSquared(Point other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
    public record Flight(List<Point> points) {
        public Flight { points = List.copyOf(points); }
        public long duration() { return points.getLast().tick() - points.getFirst().tick(); }
    }
    public record Course(List<Point> points, double estimatedTicks) {
        public Course { points = List.copyOf(points); }
    }

    private record Cell(int x, int y, int z) {}
    private record Step(int node, double cost) {}
    private record QueueEntry(int node, double cost) {}
    private static final double JOIN_DISTANCE_SQUARED = 0.9 * 0.9;

    private CourseGraph() {}

    public static Course fastest(List<Flight> flights) {
        if (flights.isEmpty()) return null;
        List<Point> points = new ArrayList<>();
        List<Integer> owners = new ArrayList<>();
        List<List<Step>> edges = new ArrayList<>();
        List<Integer> starts = new ArrayList<>(), ends = new ArrayList<>();
        Map<Cell, List<Integer>> cells = new HashMap<>();

        for (int flightIndex = 0; flightIndex < flights.size(); flightIndex++) {
            var flight = flights.get(flightIndex);
            if (flight.points().size() < 2 || flight.duration() <= 0) continue;
            starts.add(points.size());
            for (Point point : flight.points()) {
                int index = points.size();
                points.add(point); owners.add(flightIndex); edges.add(new ArrayList<>());
                cells.computeIfAbsent(cell(point), ignored -> new ArrayList<>()).add(index);
                if (index > starts.getLast()) {
                    Point previous = points.get(index - 1);
                    long ticks = point.tick() - previous.tick();
                    if (ticks <= 0) return null;
                    edges.get(index - 1).add(new Step(index, ticks));
                }
            }
            ends.add(points.size() - 1);
        }
        if (points.isEmpty()) return null;

        // A join is only possible where two observed flight centers occupied nearly
        // the same block and were moving in roughly the same direction. A shortcut
        // across open space or a wall is never inferred from distant tracks.
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i);
            Cell cell = cell(a);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    var nearby = cells.get(new Cell(cell.x() + dx, cell.y() + dy, cell.z() + dz));
                    if (nearby == null) continue;
                    for (int j : nearby) {
                        if (j >= i || owners.get(i).equals(owners.get(j))) continue;
                        Point b = points.get(j);
                        if (!cell.equals(cell(b)) || a.distanceSquared(b) > JOIN_DISTANCE_SQUARED
                            || !sameHeading(points, owners, i, j)) continue;
                        edges.get(i).add(new Step(j, 1));
                        edges.get(j).add(new Step(i, 1));
                    }
                }
        }

        int count = points.size();
        double[] distance = new double[count];
        int[] previous = new int[count];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        Arrays.fill(previous, -1);
        var queue = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::cost));
        for (int start : starts) { distance[start] = 0; queue.add(new QueueEntry(start, 0)); }
        while (!queue.isEmpty()) {
            var current = queue.remove();
            if (current.cost() > distance[current.node()]) continue;
            for (Step edge : edges.get(current.node())) {
                double next = current.cost() + edge.cost();
                if (next >= distance[edge.node()]) continue;
                distance[edge.node()] = next;
                previous[edge.node()] = current.node();
                queue.add(new QueueEntry(edge.node(), next));
            }
        }
        int best = -1;
        for (int end : ends) if (best < 0 || distance[end] < distance[best]) best = end;
        if (best < 0 || !Double.isFinite(distance[best])) return null;
        var route = new ArrayList<Point>();
        for (int at = best; at >= 0; at = previous[at]) route.add(points.get(at));
        java.util.Collections.reverse(route);
        return new Course(route, distance[best]);
    }

    private static Cell cell(Point point) {
        return new Cell((int)Math.floor(point.x()), (int)Math.floor(point.y()), (int)Math.floor(point.z()));
    }

    private static boolean sameHeading(List<Point> points, List<Integer> owners, int a, int b) {
        double[] first = heading(points, owners, a), second = heading(points, owners, b);
        if (first == null || second == null) return false;
        double dot = first[0] * second[0] + first[1] * second[1] + first[2] * second[2];
        double lengths = Math.sqrt((first[0] * first[0] + first[1] * first[1] + first[2] * first[2])
            * (second[0] * second[0] + second[1] * second[1] + second[2] * second[2]));
        return lengths > 0 && dot / lengths >= 0.5;
    }

    private static double[] heading(List<Point> points, List<Integer> owners, int index) {
        int before = index > 0 && owners.get(index - 1).equals(owners.get(index)) ? index - 1 : index;
        int after = index + 1 < points.size() && owners.get(index + 1).equals(owners.get(index)) ? index + 1 : index;
        if (before == after) return null;
        Point a = points.get(before), b = points.get(after);
        return new double[] {b.x() - a.x(), b.y() - a.y(), b.z() - a.z()};
    }
}
