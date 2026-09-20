package dev.monumentscout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class MonumentStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public enum State { DISCOVERED, SPONGES_REMAIN, NO_SPONGES, CLEARED }
    public static final class Entry {
        public int centerX, centerZ, sponges = -1, elders = -1;
        public long discoveredAt, surveyedAt;
        public boolean manual, everHadSponges;
        public transient boolean fresh;
        public Entry(int x, int z, boolean manual, long now) {
            centerX = x; centerZ = z; this.manual = manual; discoveredAt = now;
        }
        public MonumentBounds bounds() { return new MonumentBounds(centerX, centerZ); }
        public State state() {
            if (sponges < 0) return State.DISCOVERED;
            if (sponges > 0) return State.SPONGES_REMAIN;
            return elders == 0 ? State.CLEARED : State.NO_SPONGES;
        }
        public String label() {
            String status = switch (state()) {
                case DISCOVERED -> "Monument: survey pending";
                case SPONGES_REMAIN -> "Monument: " + sponges + " sponges";
                case NO_SPONGES -> "Monument: no sponges";
                case CLEARED -> "Monument: cleared (observed)";
            };
            return status + " [elders: " + (elders < 0 ? "?" : elders) + "]"
                + (surveyedAt > 0 && !fresh ? " [last survey]" : "");
        }
    }
    private static final class Data {
        int schema = 1;
        Map<String, Entry> monuments = new TreeMap<>();
    }
    private final Path file;
    private Data data = new Data();
    private boolean dirty;
    public MonumentStore(Path folder, String world, String dimension) throws IOException {
        file = folder.resolve(key(world + "\n" + dimension) + ".json");
        if (Files.exists(file)) {
            try {
                data = JSON.fromJson(Files.readString(file), Data.class);
                if (data == null || data.schema != 1 || data.monuments == null)
                    throw new IllegalArgumentException("Unsupported data");
                for (var record : data.monuments.entrySet()) {
                    var e = record.getValue();
                    if (e == null || !record.getKey().equals(e.bounds().key())
                        || e.sponges < -1 || e.elders < -1)
                        throw new IllegalArgumentException("Invalid monument record");
                }
            } catch (RuntimeException ex) { throw new IOException("Cannot read " + file + "; original retained", ex); }
        }
    }
    static String key(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public boolean discover(MonumentBounds bounds, boolean manual, long now) {
        if (data.monuments.containsKey(bounds.key())) return false;
        data.monuments.put(bounds.key(), new Entry(bounds.centerX(), bounds.centerZ(), manual, now));
        dirty = true; return true;
    }
    public void update(Entry e, int sponges, int elders, long now) {
        if (sponges < 0 || elders < -1) throw new IllegalArgumentException("Invalid survey");
        e.sponges = sponges; e.elders = elders; e.everHadSponges |= sponges > 0;
        e.surveyedAt = now; e.fresh = true; dirty = true;
    }
    public void observeElders(Entry e, int count) { e.elders = count; dirty = true; }
    public Entry get(MonumentBounds bounds) { return data.monuments.get(bounds.key()); }
    public Collection<Entry> entries() { return Collections.unmodifiableCollection(data.monuments.values()); }
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
