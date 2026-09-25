package dev.seedscout;

import com.google.common.hash.Hashing;
import java.util.ArrayList;
import java.util.List;

final class WorldSeedResolver {
    private static final long STRUCTURE_MASK = (1L << 48) - 1;

    private WorldSeedResolver() { }

    @SuppressWarnings("deprecation")
    static List<Long> resolve(long[] structureSeeds, long biomeZoomSeed) {
        var result = new ArrayList<Long>();
        var sha256 = Hashing.sha256();
        for (long structureSeed : structureSeeds) {
            long lower = structureSeed & STRUCTURE_MASK;
            for (long upper = 0; upper < 1L << 16; upper++) {
                long candidate = (upper << 48) | lower;
                if (sha256.hashLong(candidate).asLong() == biomeZoomSeed) result.add(candidate);
            }
        }
        return List.copyOf(result);
    }
}
