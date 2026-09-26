package dev.frydae.slime;

import java.util.*;

/** Client packets have no parent UUID; correlate visible deaths with half-size children. */
final class SplitFilter {
    static final int GRACE_TICKS = 5;
    static final int DEATH_WINDOW_TICKS = 60;
    enum Verdict { WAIT, ALLOW, SPLIT }
    record Sample(UUID id, int size, double x, double y, double z, double width) {
        int chunkX() { return ((int) Math.floor(x)) >> 4; }
        int chunkZ() { return ((int) Math.floor(z)) >> 4; }
        int blockY() { return (int) Math.floor(y); }
    }
    private record Death(Sample parent, long tick) { }
    private static final class Pending {
        final Sample first;
        final long tick;
        Verdict verdict = Verdict.WAIT;
        Pending(Sample first, long tick) { this.first = first; this.tick = tick; }
    }
    private final Map<UUID, Death> deaths = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();

    void death(Sample parent, long tick) {
        if (parent.size() > 1) deaths.put(parent.id(), new Death(parent, tick));
    }
    Verdict observe(Sample slime, long tick) {
        Pending p = pending.computeIfAbsent(slime.id(), ignored -> new Pending(slime, tick));
        if (p.verdict != Verdict.WAIT) return p.verdict;
        for (Death death : deaths.values()) {
            var parent = death.parent();
            if (tick - death.tick() > DEATH_WINDOW_TICKS || p.tick < death.tick() - GRACE_TICKS
                || parent.id().equals(slime.id()) || slime.size() != parent.size() / 2) continue;
            double reach = parent.width() / 4.0 + 1.0;
            if (Math.abs(p.first.x() - parent.x()) <= reach && Math.abs(p.first.z() - parent.z()) <= reach
                && Math.abs(p.first.y() - (parent.y() + 0.5)) <= 1.5) {
                p.verdict = Verdict.SPLIT;
                return p.verdict;
            }
        }
        if (tick - p.tick >= GRACE_TICKS) p.verdict = Verdict.ALLOW;
        return p.verdict;
    }
    Sample first(UUID id) { return pending.get(id).first; }
    void retain(Set<UUID> loaded, long tick) {
        pending.keySet().retainAll(loaded);
        deaths.values().removeIf(death -> tick - death.tick() > DEATH_WINDOW_TICKS);
    }
    void clear() { deaths.clear(); pending.clear(); }
}
