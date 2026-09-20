package dev.endcityscout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import dev.endcityscout.CitySignature.Pos;

public final class CityStore {
    public static final int RADIUS = 192;
    public enum Presence { UNKNOWN, PRESENT, ABSENT }
    public static final class City {
        public Pos center;
        public boolean manual, searched;
        public int shulkers = -1;
        public long surveyedAt;
        public transient boolean fresh, searchFresh;
        City(Pos center, boolean manual) { this.center = center; this.manual = manual; }
        public boolean contains(double x, double z) {
            return Math.abs(center.x() - x) <= RADIUS && Math.abs(center.z() - z) <= RADIUS;
        }
    }
    public static final class Ship {
        public Pos frame;
        public Presence elytra = Presence.UNKNOWN;
        public transient boolean fresh;
        Ship(Pos frame) { this.frame = frame; }
    }
    private static final class Data {
        int schema = 2;
        Map<String, City> cities = new TreeMap<>();
        Map<String, Ship> ships = new TreeMap<>();
    }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private Data data = new Data();
    private List<City> centers;
    private boolean dirty;
    public CityStore(Path folder, String world, String dimension) throws IOException {
        file = folder.resolve(key(world + "\n" + dimension) + ".json");
        if (!Files.exists(file)) return;
        try {
            data = JSON.fromJson(Files.readString(file), Data.class);
            if (data == null || (data.schema != 1 && data.schema != 2) || data.cities == null || data.ships == null)
                throw new IllegalArgumentException("Unsupported records");
            for (var pair : data.cities.entrySet()) {
                var c = pair.getValue();
                if (c == null || !valid(c.center) || !pair.getKey().equals(c.center.key()) || c.shulkers < -1)
                    throw new IllegalArgumentException("Invalid city");
            }
            for (var pair : data.ships.entrySet()) {
                var s = pair.getValue();
                if (s == null || !valid(s.frame) || !pair.getKey().equals(s.frame.key()) || s.elytra == null)
                    throw new IllegalArgumentException("Invalid ship");
            }
            if (data.schema == 1) {
                // Old counts were divided between branch towers. Do not present one
                // tower's zero as a result for the newly combined city.
                for (var c : cities())
                    if (data.cities.values().stream().filter(other -> c.contains(other.center.x(), other.center.z())).count() > 1)
                        resetSurvey(c);
                data.schema = 2; dirty = true;
            }
        } catch (RuntimeException ex) { throw new IOException("Cannot read " + file + "; original retained", ex); }
    }
    private static boolean valid(Pos p) { return p != null && Math.abs((long)p.x()) < 30_000_000 && Math.abs((long)p.z()) < 30_000_000 && p.y() >= -2048 && p.y() <= 2048; }
    static String key(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public Collection<City> cities() {
        if (centers == null) {
            var ordered = new ArrayList<>(data.cities.values());
            // End city branch houses reuse base_floor. The lowest entrance is the
            // main base; manual anchors take priority. Never chain groups via towers.
            ordered.sort(Comparator.<City>comparingInt(c -> c.manual ? 0 : 1)
                .thenComparingInt(c -> c.center.y())
                .thenComparingDouble(this::centrality).thenComparing(c -> c.center.key()));
            var selected = new ArrayList<City>();
            for (var candidate : ordered)
                if (selected.stream().noneMatch(c -> c.contains(candidate.center.x(), candidate.center.z())))
                    selected.add(candidate);
            centers = List.copyOf(selected);
        }
        return centers;
    }
    private double centrality(City city) {
        return data.cities.values().stream().filter(c -> city.contains(c.center.x(), c.center.z()))
            .mapToDouble(c -> city.center.distanceSquared(c.center.x(), c.center.z())).sum();
    }
    private static void resetSurvey(City c) {
        c.shulkers = -1; c.searched = false; c.surveyedAt = 0;
        c.fresh = false; c.searchFresh = false;
    }
    public Collection<Ship> ships() { return Collections.unmodifiableCollection(data.ships.values()); }
    public boolean isRecordedEntrance(int x, int z) {
        return data.cities.values().stream().anyMatch(c -> c.center.x() == x && c.center.z() == z);
    }
    public boolean discover(Pos center, boolean manual) {
        // Retain raw detections so discovery order cannot determine the city center.
        if (data.cities.values().stream().anyMatch(c -> c.center.distanceSquared(center.x(), center.z()) <= 16 * 16)) return false;
        data.cities.put(center.key(), new City(center, manual)); centers = null;
        var affected = owner(center.x(), center.z());
        if (affected != null) resetSurvey(affected);
        dirty = true; return true;
    }
    public boolean discoverShip(Pos frame) {
        if (data.ships.containsKey(frame.key())) return false;
        data.ships.put(frame.key(), new Ship(frame)); dirty = true; return true;
    }
    public City owner(double x, double z) {
        return cities().stream().filter(c -> c.contains(x, z))
            .min(Comparator.<City>comparingDouble(c -> c.center.distanceSquared(x, z)).thenComparing(c -> c.center.key())).orElse(null);
    }
    public List<Ship> ships(City city) { return ships().stream().filter(s -> owner(s.frame.x(), s.frame.z()) == city).toList(); }
    public void observe(City city, int count, boolean fresh, long now) {
        if (count < -1) throw new IllegalArgumentException("count");
        city.shulkers = count; city.fresh = fresh; city.surveyedAt = now; dirty = true;
    }
    public void searched(City city) {
        if (!city.searched) { city.searched = true; dirty = true; }
        city.searchFresh = true;
    }
    public void observe(Ship ship, Presence presence) {
        if (ship.elytra != presence) { ship.elytra = presence; dirty = true; }
        ship.fresh = true;
    }
    public boolean hasElytra(City city) {
        return ships(city).stream().anyMatch(s -> s.elytra == Presence.PRESENT);
    }
    private String shulkerCount(City city) { return city.shulkers < 0 ? "?" : Integer.toString(city.shulkers); }
    public String label(City city) {
        return "End City" + (hasElytra(city) ? " (Elytra)" : "") + " (" + shulkerCount(city) + " Shulkers)";
    }
    public String symbol(City city) {
        return "EC-" + (hasElytra(city) ? "E" : "") + shulkerCount(city);
    }
    public void save() throws IOException {
        if (!dirty) return;
        Files.createDirectories(file.getParent());
        var tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, JSON.toJson(data), StandardCharsets.UTF_8);
        try { Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        dirty = false;
    }
}
