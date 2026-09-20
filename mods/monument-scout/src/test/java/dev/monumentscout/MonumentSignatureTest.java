package dev.monumentscout;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.monumentscout.MonumentSignature.Block.*;

class MonumentSignatureTest {
    private Map<String, MonumentSignature.Block> crown(int x, int z) {
        var blocks = new HashMap<String, MonumentSignature.Block>();
        for (int dx : new int[] {-2, 1}) for (int dz : new int[] {-2, 1}) {
            blocks.put((x + dx) + ",59," + (z + dz), LANTERN);
            blocks.put((x + dx) + ",60," + (z + dz), BRICKS);
        }
        for (int inner : new int[] {-1, 0}) for (int outer : new int[] {-2, 1}) {
            blocks.put((x + inner) + ",60," + (z + outer), PRISMARINE);
            blocks.put((x + outer) + ",60," + (z + inner), PRISMARINE);
        }
        return blocks;
    }
    @Test void detectsCrownAtPositiveAndNegativeChunkAlignedAnchors() {
        for (int x : new int[] {-128, 0, 256}) {
            var map = crown(x, -64);
            assertTrue(MonumentSignature.matches(x, -64, (a, b, c) -> map.getOrDefault(a + "," + b + "," + c, OTHER)));
        }
    }
    @Test void incompleteOrGenericPrismarineBuildDoesNotMatch() {
        assertFalse(MonumentSignature.matches(0, 0, (x, y, z) -> PRISMARINE));
        var map = crown(0, 0);
        map.put("-2,59,-2", UNLOADED);
        assertFalse(MonumentSignature.matches(0, 0, (a, b, c) -> map.getOrDefault(a + "," + b + "," + c, OTHER)));
    }
    @Test void boundsHandleNegativeChunksAndElderSurveyRange() {
        var b = new MonumentBounds(-16, -16);
        assertTrue(b.containsChunk(-3, -3));
        assertTrue(b.containsChunk(0, 0));
        assertFalse(b.containsChunk(1, 0));
        assertTrue(b.containsElder(-16, 55, -16));
        assertFalse(b.containsElder(200, 55, -16));
        assertTrue(b.canSurveyElders(-16, 63, -16));
        assertFalse(b.canSurveyElders(-16, 150, -16));
        assertThrows(IllegalArgumentException.class, () -> new MonumentBounds(Integer.MIN_VALUE, 0));
    }
}
