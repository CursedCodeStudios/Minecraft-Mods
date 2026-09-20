package dev.monumentscout;

/** Rotation-invariant crown signature from the vanilla 26.2 monument generator. */
final class MonumentSignature {
    enum Block { LANTERN, BRICKS, PRISMARINE, OTHER, UNLOADED }
    interface Reader { Block get(int x, int y, int z); }
    static boolean matches(int x, int z, Reader reader) {
        for (int dx : new int[] {-2, 1}) {
            for (int dz : new int[] {-2, 1}) {
                if (reader.get(x + dx, 59, z + dz) != Block.LANTERN
                    || reader.get(x + dx, 60, z + dz) != Block.BRICKS) return false;
            }
        }
        for (int inner : new int[] {-1, 0}) {
            for (int outer : new int[] {-2, 1}) {
                if (reader.get(x + inner, 60, z + outer) != Block.PRISMARINE
                    || reader.get(x + outer, 60, z + inner) != Block.PRISMARINE) return false;
            }
        }
        return true;
    }
}
