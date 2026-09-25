package dev.bedrockscout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class BedrockStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final class Entry {
        public int x, y, z;
        public long discoveredAt;
        Entry(int x, int y, int z, long now) { this.x = x; this.y = y; this.z = z; discoveredAt = now; }
        String key() { return x + "," + z; }
        String label() { return "Nether Roof 3x3 (Y=" + y + ")"; }
    }
    private static final class Data {
        int schema = 1;
        Map<String, Entry> patterns = new TreeMap<>();
    }
    private final Path file;
    private Data data = new Data();
    private boolean dirty;
    public BedrockStore(Path folder, String world, String dimension) throws IOException {
        file = folder.resolve(hash(world + "\n" + dimension) + ".json");
        if (!Files.exists(file)) return;
        try {
            data = JSON.fromJson(Files.readString(file), Data.class);
            if (data == null || data.schema != 1 || data.patterns == null) throw new IllegalArgumentException("Unsupported data");
            for (var pair : data.patterns.entrySet()) {
                var e = pair.getValue();
                if (e == null || !pair.getKey().equals(e.key()) || e.y < BedrockPattern.MIN_Y || e.y > BedrockPattern.MAX_Y
                    || Math.abs((long)e.x) >= 30_000_000 || Math.abs((long)e.z) >= 30_000_000)
                    throw new IllegalArgumentException("Invalid pattern record");
            }
        } catch (RuntimeException ex) { throw new IOException("Cannot read " + file + "; original retained", ex); }
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public Entry discover(int x, int y, int z, long now) {
        String key = x + "," + z;
        var old = data.patterns.get(key);
        if (old != null && old.y <= y) return null;
        var entry = new Entry(x, y, z, now);
        data.patterns.put(key, entry); dirty = true; return entry;
    }
    public Collection<Entry> entries() { return Collections.unmodifiableCollection(data.patterns.values()); }
    public Entry nearest(double x, double z, double radius) {
        double limit = radius * radius;
        return entries().stream().filter(e -> distance(e, x, z) <= limit)
            .min(Comparator.comparingDouble(e -> distance(e, x, z))).orElse(null);
    }
    private static double distance(Entry e, double x, double z) { return (e.x - x) * (e.x - x) + (e.z - z) * (e.z - z); }
    public void save() throws IOException {
        if (!dirty) return;
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(data), StandardCharsets.UTF_8);
        try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        dirty = false;
    }
}
