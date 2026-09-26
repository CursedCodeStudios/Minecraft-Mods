package dev.frydae.endcity;

import java.util.*;
import dev.frydae.endcity.CitySignature.Pos;

/** Coverage evidence from one uninterrupted block sweep; never persisted across sessions. */
final class SurveyCoverage {
    private final Set<Long> chunks = new HashSet<>();
    private final Set<Pos> structures = new HashSet<>();
    private boolean valid = true, finished;
    private static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
    void scannedChunk(int x, int z) { chunks.add(key(x, z)); }
    void structureSection(Pos p) { structures.add(p); }
    void finish() { finished = true; }
    void invalidate() { valid = false; }
    boolean covers(CityStore.City city) {
        if (!valid || !finished) return false;
        // The halo supplies template reads crossing the city's search boundary.
        for (int x = (city.center.x() - CityStore.RADIUS - 32) >> 4; x <= (city.center.x() + CityStore.RADIUS + 32) >> 4; x++)
            for (int z = (city.center.z() - CityStore.RADIUS - 32) >> 4; z <= (city.center.z() + CityStore.RADIUS + 32) >> 4; z++)
                if (!chunks.contains(key(x, z))) return false;
        return true;
    }
    boolean entityCoverage(CityStore.City city, double x, double y, double z) {
        if (!covers(city)) return false;
        boolean found = false;
        for (var p : structures) {
            // Include sections intersecting the search area, even at its edges.
            if (p.x() > city.center.x() + CityStore.RADIUS || p.x() + 15 < city.center.x() - CityStore.RADIUS
                || p.z() > city.center.z() + CityStore.RADIUS || p.z() + 15 < city.center.z() - CityStore.RADIUS) continue;
            found = true;
            // Include a 16-block teleport/wandering margin on each structure section.
            if (Math.max(Math.abs(p.x() - 16 - x), Math.abs(p.x() + 31 - x)) > 64
                || Math.max(Math.abs(p.y() - 16 - y), Math.abs(p.y() + 31 - y)) > 64
                || Math.max(Math.abs(p.z() - 16 - z), Math.abs(p.z() + 31 - z)) > 64) return false;
        }
        return found;
    }
}
