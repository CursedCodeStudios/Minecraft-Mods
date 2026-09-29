package dev.frydae.utilities.course;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** One local Nether course per world or server. No route data is sent to the server. */
public final class CourseStore {
    public record Anchor(double x, double y, double z) {
        public double distanceSquared(CourseGraph.Point point) {
            double dx = x - point.x(), dy = y - point.y(), dz = z - point.z();
            return dx * dx + dy * dy + dz * dz;
        }
    }
    private static final class Data {
        int schema = 1;
        Anchor a, b;
        List<CourseGraph.Flight> forward = new ArrayList<>(), reverse = new ArrayList<>();
    }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_FLIGHTS = 8, MAX_POINTS = 4_000;
    private final Path file;
    private Data data = new Data();

    public CourseStore(Path folder, String world) throws IOException {
        file = folder.resolve("elytra-course-" + key(world) + ".json");
        if (!Files.exists(file)) return;
        try {
            data = JSON.fromJson(Files.readString(file), Data.class);
            if (data == null || data.schema != 1 || data.forward == null || data.reverse == null
                || data.forward.size() > MAX_FLIGHTS || data.reverse.size() > MAX_FLIGHTS
                || !valid(data.a) || !valid(data.b)) throw new IllegalArgumentException("Invalid course");
            validate(data.forward); validate(data.reverse);
        } catch (RuntimeException ex) {
            throw new IOException("Cannot read " + file + "; original retained", ex);
        }
    }

    public Anchor a() { return data.a; }
    public Anchor b() { return data.b; }
    public List<CourseGraph.Flight> flights(boolean forward) {
        return List.copyOf(forward ? data.forward : data.reverse);
    }

    public void setAnchor(boolean first, Anchor anchor) throws IOException {
        if (anchor == null || !valid(anchor)) throw new IllegalArgumentException("Invalid endpoint");
        Data next = copy();
        if (first) next.a = anchor; else next.b = anchor;
        next.forward.clear(); next.reverse.clear();
        save(next); data = next;
    }

    public void addFlight(boolean forward, CourseGraph.Flight flight) throws IOException {
        validate(List.of(flight));
        Data next = copy();
        List<CourseGraph.Flight> list = forward ? next.forward : next.reverse;
        list.add(flight);
        if (list.size() > MAX_FLIGHTS) list.removeFirst();
        save(next); data = next;
    }

    public void clearFlights() throws IOException {
        Data next = copy();
        next.forward.clear(); next.reverse.clear();
        save(next); data = next;
    }

    private Data copy() {
        Data next = new Data();
        next.a = data.a; next.b = data.b;
        next.forward = new ArrayList<>(data.forward);
        next.reverse = new ArrayList<>(data.reverse);
        return next;
    }

    private void save(Data next) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(next), StandardCharsets.UTF_8);
        try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void validate(List<CourseGraph.Flight> flights) {
        for (var flight : flights) {
            if (flight == null || flight.points() == null || flight.points().size() < 2
                || flight.points().size() > MAX_POINTS) throw new IllegalArgumentException("Invalid flight");
            long previous = -1;
            for (var point : flight.points()) {
                if (point == null || !valid(point.x(), point.y(), point.z()) || point.tick() <= previous)
                    throw new IllegalArgumentException("Invalid flight point");
                previous = point.tick();
            }
        }
    }

    private static boolean valid(Anchor anchor) {
        return anchor == null || valid(anchor.x(), anchor.y(), anchor.z());
    }
    private static boolean valid(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && Math.abs(x) < 30_000_000 && Math.abs(z) < 30_000_000 && y >= -2048 && y <= 2048;
    }
    private static String key(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
