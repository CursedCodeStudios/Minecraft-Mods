package dev.slimescout;

import java.util.*;

/** Session-only entity tracking: entering another chunk is not fresh spawn evidence. */
final class ObservationTracker {
    enum Kind { NONE, FIRST, MOVED }
    private final Map<UUID, Long> chunks = new HashMap<>();
    Kind observe(UUID entity, long chunk) {
        Long previous = chunks.put(entity, chunk);
        return previous == null ? Kind.FIRST : previous == chunk ? Kind.NONE : Kind.MOVED;
    }
    void retain(Set<UUID> loaded) { chunks.keySet().retainAll(loaded); }
    void clear() { chunks.clear(); }
    void unload(long chunk) { chunks.values().removeIf(value -> value == chunk); }
}
