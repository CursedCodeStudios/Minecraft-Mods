package dev.bedrockscout;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockPatternTest {
    private record Pos(int x, int y, int z) { }
    private static final class Reader implements BedrockPattern.Reader {
        final Set<Pos> bedrock = new HashSet<>();
        final Set<String> unloaded = new HashSet<>();
        @Override public boolean loaded(int x, int z) { return !unloaded.contains((x >> 4) + "," + (z >> 4)); }
        @Override public boolean bedrock(int x, int y, int z) { return bedrock.contains(new Pos(x, y, z)); }
        void square(int x, int y, int z) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) bedrock.add(new Pos(x + dx, y, z + dz));
        }
    }

    @Test void acceptsEveryRoofBandHeightWithClearSpaceUnderneath() {
        for (int y = BedrockPattern.MIN_Y; y <= BedrockPattern.MAX_Y; y++) {
            var reader = new Reader(); reader.square(-16, y, 31);
            assertTrue(BedrockPattern.matches(-16, y, 31, reader), "Y=" + y);
        }
    }

    @Test void anyMissingSquareBlockOrBedrockAnywhereUnderneathRejectsIt() {
        var reader = new Reader(); reader.square(0, 126, 0);
        reader.bedrock.remove(new Pos(1, 126, -1));
        assertFalse(BedrockPattern.matches(0, 126, 0, reader));
        reader.bedrock.add(new Pos(1, 126, -1));
        reader.bedrock.add(new Pos(-1, 124, 1));
        assertFalse(BedrockPattern.matches(0, 126, 0, reader));
    }

    @Test void unloadedCrossChunkDataCannotCreateAFalseMatch() {
        var reader = new Reader(); reader.square(15, 126, 15); reader.unloaded.add("1,1");
        assertFalse(BedrockPattern.matches(15, 126, 15, reader));
    }

    @Test void ignoresBlocksOutsideTheNetherCeilingBand() {
        var reader = new Reader(); reader.square(0, 122, 0); reader.square(0, 128, 0);
        assertFalse(BedrockPattern.matches(0, 122, 0, reader));
        assertFalse(BedrockPattern.matches(0, 128, 0, reader));
    }
}
