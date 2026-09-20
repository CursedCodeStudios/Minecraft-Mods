package dev.slimescout;

import java.util.*;

/** Tracks object identity, so renaming a waypoint is not mistaken for deletion. */
final class WaypointOwnership<T> {
    private final Map<String, T> owned = new HashMap<>();
    T remove(String key) { return owned.remove(key); }
    T get(String key) { return owned.get(key); }
    void put(String key, T value) { owned.put(key, value); }
    Collection<T> values() { return owned.values(); }
    boolean isEmpty() { return owned.isEmpty(); }
    void clear() { owned.clear(); }
    Set<String> deletedFrom(Collection<T> present) {
        Set<T> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        identities.addAll(present);
        Set<String> deleted = new HashSet<>();
        owned.entrySet().removeIf(entry -> {
            if (identities.contains(entry.getValue())) return false;
            deleted.add(entry.getKey()); return true;
        });
        return deleted;
    }
}
