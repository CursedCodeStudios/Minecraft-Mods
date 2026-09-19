package dev.slimescout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class SightingStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final class Entry {
        public int x, z, minY;
        public long firstSeen, lastSeen, observations, visits;
        public boolean underground;
        public Entry(int x, int z, int y, long now) {
            this.x = x; this.z = z; minY = y; firstSeen = now;
        }
    }
    private static final class Data {
        int schema = 2;
        Map<String, Entry> chunks = new TreeMap<>();
    }
    private final Path file;
    private Data data = new Data();
    private boolean dirty;
    private final Set<String> countedThisLoad = new HashSet<>();

    public SightingStore(Path folder, String world, String dimension) throws IOException {
        file = folder.resolve(key(world + "\n" + dimension) + ".json");
        if (Files.exists(file)) {
            try {
                data = JSON.fromJson(Files.readString(file), Data.class);
                if (data == null || (data.schema != 1 && data.schema != 2) || data.chunks == null) throw new IllegalArgumentException("Unsupported data");
                for (var e : data.chunks.entrySet()) {
                    if (e.getValue() == null || !e.getKey().equals(e.getValue().x + "," + e.getValue().z))
                        throw new IllegalArgumentException("Invalid chunk record");
                }
                if (data.schema == 1) {
                    // Old per-entity totals cannot tell us how many chunk loads occurred.
                    data.chunks.values().forEach(e -> e.visits = 1);
                    data.schema = 2;
                    dirty = true;
                }
            } catch (RuntimeException ex) { throw new IOException("Cannot read " + file + "; original retained", ex); }
        }
    }
    static String key(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public boolean record(int x, int z, int y, boolean candidate, long now) {
        String key = x + "," + z;
        boolean fresh = !data.chunks.containsKey(key);
        Entry e = data.chunks.computeIfAbsent(key, ignored -> new Entry(x, z, y, now));
        e.minY = Math.min(e.minY, y); e.lastSeen = now; e.observations++;
        if (countedThisLoad.add(key)) e.visits++;
        e.underground |= candidate;
        dirty = true;
        return fresh;
    }
    public Collection<Entry> entries() { return Collections.unmodifiableCollection(data.chunks.values()); }
    public Entry get(int x, int z) { return data.chunks.get(x + "," + z); }
    public void unload(int x, int z) { countedThisLoad.remove(x + "," + z); }
    public void save() throws IOException {
        if (!dirty) return;
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, JSON.toJson(data), StandardCharsets.UTF_8);
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        dirty = false;
    }
}
