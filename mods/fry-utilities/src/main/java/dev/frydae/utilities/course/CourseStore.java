package dev.frydae.utilities.course;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Named Nether courses local to one world or server. */
public final class CourseStore {
    public record Anchor(double x, double y, double z) {
        public double distanceSquared(CourseGraph.Point point) {
            double dx = x - point.x(), dy = y - point.y(), dz = z - point.z();
            return dx * dx + dy * dy + dz * dz;
        }
    }
    public static final class Course {
        private String name, aName = "A", bName = "B";
        private Anchor a, b;
        private List<CourseGraph.Flight> forward = new ArrayList<>(), reverse = new ArrayList<>();
        private Course(String name) { this.name = name; }
        private Course copy() {
            Course next = new Course(name);
            next.aName = aName; next.bName = bName; next.a = a; next.b = b;
            next.forward = new ArrayList<>(forward); next.reverse = new ArrayList<>(reverse);
            return next;
        }
        public String name() { return name; }
        public String aName() { return aName; }
        public String bName() { return bName; }
        public Anchor a() { return a; }
        public Anchor b() { return b; }
        public List<CourseGraph.Flight> flights(boolean fromA) {
            return List.copyOf(fromA ? forward : reverse);
        }
    }
    private static final class Data {
        int schema = 2;
        String selected = keyName("Default");
        Map<String, Course> courses = new LinkedHashMap<>();
        Data() { courses.put(selected, new Course("Default")); }
    }
    private static final class LegacyData {
        int schema;
        Anchor a, b;
        List<CourseGraph.Flight> forward, reverse;
    }
    private record Transfer(String format, int schema, String dimension, Course course) {}
    private static final String TRANSFER_FORMAT = "fry-utilities-elytra-course";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_COURSES = 32, MAX_FLIGHTS = 8, MAX_POINTS = 4_000;
    private final Path file;
    private Data data = new Data();

    public CourseStore(Path folder, String world) throws IOException {
        file = folder.resolve("elytra-course-" + key(world) + ".json");
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file);
            int schema = JsonParser.parseString(json).getAsJsonObject().get("schema").getAsInt();
            if (schema == 1) data = migrate(JSON.fromJson(json, LegacyData.class));
            else if (schema == 2) data = JSON.fromJson(json, Data.class);
            else throw new IllegalArgumentException("Unsupported course schema");
            validate(data);
        } catch (RuntimeException ex) {
            throw new IOException("Cannot read " + file + "; original retained", ex);
        }
    }

    public Collection<Course> courses() { return List.copyOf(data.courses.values()); }
    public List<String> names() { return data.courses.values().stream().map(Course::name).toList(); }
    public Course selected() { return data.courses.get(data.selected); }
    public Course get(String name) { return data.courses.get(keyName(name)); }

    public boolean create(String name) throws IOException {
        requireName(name);
        String key = keyName(name);
        if (data.courses.containsKey(key) || data.courses.size() >= MAX_COURSES) return false;
        Data next = copy(); next.courses.put(key, new Course(name)); next.selected = key;
        save(next); data = next; return true;
    }

    public boolean select(String name) throws IOException {
        String key = keyName(name);
        if (!data.courses.containsKey(key)) return false;
        Data next = copy(); next.selected = key;
        save(next); data = next; return true;
    }

    public boolean rename(String oldName, String newName) throws IOException {
        requireName(newName);
        String oldKey = keyName(oldName), newKey = keyName(newName);
        if (!data.courses.containsKey(oldKey) || (!oldKey.equals(newKey) && data.courses.containsKey(newKey))) return false;
        Data next = copy(); Course course = next.courses.remove(oldKey); course.name = newName;
        next.courses.put(newKey, course);
        if (next.selected.equals(oldKey)) next.selected = newKey;
        save(next); data = next; return true;
    }

    public boolean delete(String name) throws IOException {
        String key = keyName(name);
        if (!data.courses.containsKey(key) || data.courses.size() == 1) return false;
        Data next = copy(); next.courses.remove(key);
        if (next.selected.equals(key)) next.selected = next.courses.keySet().iterator().next();
        save(next); data = next; return true;
    }

    public boolean renameEndpoint(boolean first, String name) throws IOException {
        requireName(name);
        Course active = selected();
        if (keyName(name).equals(keyName(first ? active.bName : active.aName))) return false;
        Data next = copy(); Course course = next.courses.get(next.selected);
        if (first) course.aName = name; else course.bName = name;
        save(next); data = next; return true;
    }

    public void setAnchor(boolean first, Anchor anchor) throws IOException {
        if (anchor == null || !valid(anchor)) throw new IllegalArgumentException("Invalid endpoint");
        Data next = copy(); Course course = next.courses.get(next.selected);
        if (first) course.a = anchor; else course.b = anchor;
        course.forward.clear(); course.reverse.clear();
        save(next); data = next;
    }

    public void addFlight(String courseName, boolean forward, CourseGraph.Flight flight) throws IOException {
        validate(List.of(flight));
        Data next = copy(); Course course = next.courses.get(keyName(courseName));
        if (course == null) throw new IllegalArgumentException("Unknown course");
        List<CourseGraph.Flight> list = forward ? course.forward : course.reverse;
        list.add(flight);
        if (list.size() > MAX_FLIGHTS) list.removeFirst();
        save(next); data = next;
    }

    public void clearFlights() throws IOException {
        Data next = copy(); Course course = next.courses.get(next.selected);
        course.forward.clear(); course.reverse.clear();
        save(next); data = next;
    }

    public String exportCourse(String name) {
        Course course = get(name);
        if (course == null) throw new IllegalArgumentException("No course named " + name + ".");
        validateTransferCourse(course);
        return JSON.toJson(new Transfer(TRANSFER_FORMAT, 1, "minecraft:the_nether", course));
    }

    public Course importCourse(String json, String newName) throws IOException {
        Course imported;
        try {
            Transfer transfer = JSON.fromJson(json, Transfer.class);
            if (transfer == null || !TRANSFER_FORMAT.equals(transfer.format()) || transfer.schema() != 1
                || !"minecraft:the_nether".equals(transfer.dimension()))
                throw new IllegalArgumentException("Unsupported course export format");
            validateTransferCourse(transfer.course());
            imported = transfer.course().copy();
        } catch (RuntimeException ex) {
            throw new IOException("Invalid course export; existing courses retained", ex);
        }
        if (data.courses.size() >= MAX_COURSES)
            throw new IllegalArgumentException("The 32-course limit was reached. Delete an unused course first.");
        if (newName != null) {
            requireName(newName);
            if (get(newName) != null) throw new IllegalArgumentException("That course name is already used. Choose another import name.");
            imported.name = newName;
        } else {
            String original = imported.name;
            for (int suffix = 2; get(imported.name) != null; suffix++) {
                String tail = " " + suffix;
                imported.name = original.substring(0, Math.min(original.length(), 32 - tail.length())).stripTrailing() + tail;
            }
        }
        Data next = copy();
        next.selected = keyName(imported.name);
        next.courses.put(next.selected, imported);
        save(next); data = next;
        return selected();
    }

    private static void validateTransferCourse(Course course) {
        if (course == null || course.a == null || course.b == null)
            throw new IllegalArgumentException("Set both endpoints before exporting a course.");
        Data single = new Data(); single.courses.clear();
        requireName(course.name);
        single.selected = keyName(course.name); single.courses.put(single.selected, course);
        validate(single);
        if (course.a.distanceSquared(new CourseGraph.Point(course.b.x(), course.b.y(), course.b.z(), 0)) < 32 * 32)
            throw new IllegalArgumentException("Place endpoints at least 32 blocks apart.");
    }

    private Data copy() {
        Data next = new Data(); next.selected = data.selected; next.courses.clear();
        data.courses.forEach((key, course) -> next.courses.put(key, course.copy()));
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

    private static Data migrate(LegacyData old) {
        if (old == null || old.forward == null || old.reverse == null) throw new IllegalArgumentException("Invalid legacy course");
        Data next = new Data(); Course course = next.courses.get(next.selected);
        course.a = old.a; course.b = old.b;
        course.forward = old.forward; course.reverse = old.reverse;
        return next;
    }
    private static void validate(Data value) {
        if (value == null || value.schema != 2 || value.courses == null || value.courses.isEmpty()
            || value.courses.size() > MAX_COURSES || !value.courses.containsKey(value.selected))
            throw new IllegalArgumentException("Invalid course collection");
        for (var entry : value.courses.entrySet()) {
            Course course = entry.getValue();
            if (course == null || !validName(course.name) || !entry.getKey().equals(keyName(course.name))
                || !validName(course.aName) || !validName(course.bName)
                || keyName(course.aName).equals(keyName(course.bName))
                || !valid(course.a) || !valid(course.b) || course.forward == null || course.reverse == null
                || course.forward.size() > MAX_FLIGHTS || course.reverse.size() > MAX_FLIGHTS)
                throw new IllegalArgumentException("Invalid named course");
            validate(course.forward); validate(course.reverse);
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
    private static void requireName(String name) {
        if (!validName(name)) throw new IllegalArgumentException("Names must be 1-32 letters, numbers, spaces, _ or -.");
    }
    private static boolean validName(String name) {
        return name != null && name.equals(name.trim()) && name.length() >= 1 && name.length() <= 32
            && name.matches("[A-Za-z0-9][A-Za-z0-9 _-]*");
    }
    private static String keyName(String name) { return name.trim().toLowerCase(Locale.ROOT); }
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
