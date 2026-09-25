package dev.seedscout;

import com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldSeedResolverTest {
    @Test @SuppressWarnings("deprecation")
    void recoversSignedFullSeedFromStructureSeedAndBiomeHash() {
        long worldSeed = -1_234_567_890_123_456_789L;
        long structureSeed = worldSeed & ((1L << 48) - 1);
        long biomeHash = Hashing.sha256().hashLong(worldSeed).asLong();
        assertEquals(java.util.List.of(worldSeed), WorldSeedResolver.resolve(new long[]{structureSeed}, biomeHash));
    }

    @Test void returnsNothingForAnUnrelatedHash() {
        assertTrue(WorldSeedResolver.resolve(new long[]{1234}, Long.MIN_VALUE).isEmpty());
    }
}
