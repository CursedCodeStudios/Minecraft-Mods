package dev.bedrockscout;

/** An exposed horizontal 3x3 in the vanilla Nether-ceiling bedrock band. */
final class BedrockPattern {
    static final int MIN_Y = 123, MAX_Y = 127;
    interface Reader {
        boolean loaded(int x, int z);
        boolean bedrock(int x, int y, int z);
    }
    static boolean matches(int centerX, int y, int centerZ, Reader reader) {
        if (y < MIN_Y || y > MAX_Y) return false;
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                if (!reader.loaded(x, z) || !reader.bedrock(x, y, z)) return false;
                // Nothing unbreakable may remain under the build footprint in
                // the vanilla Nether-ceiling bedrock band.
                for (int below = MIN_Y; below < y; below++)
                    if (reader.bedrock(x, below, z)) return false;
            }
        }
        return true;
    }
}
