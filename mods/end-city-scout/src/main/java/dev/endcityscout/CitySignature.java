package dev.endcityscout;

import java.util.*;

/** Block signatures taken from the vanilla 26.2 base_floor and ship templates.
 * Loot, mobs, brewing stand and dragon head deliberately are not part of recognition. */
public final class CitySignature {
    public enum Block { OTHER, UNLOADED, PURPUR, PILLAR, BRICKS, GLASS, OBSIDIAN }
    public interface Reader { Block get(int x, int y, int z); }
    public record Pos(int x, int y, int z) {
        public String key() { return x + "," + y + "," + z; }
        public Pos offset(int dx, int dy, int dz, int rotation) {
            return switch (rotation) {
                case 0 -> new Pos(x + dx, y + dy, z + dz);
                case 1 -> new Pos(x - dz, y + dy, z + dx);
                case 2 -> new Pos(x - dx, y + dy, z - dz);
                case 3 -> new Pos(x + dz, y + dy, z - dx);
                default -> throw new IllegalArgumentException("rotation");
            };
        }
        public double distanceSquared(double px, double pz) { return (x - px) * (x - px) + (z - pz) * (z - pz); }
    }
    record Sample(int x, int y, int z, Block block) { }
    static final List<Sample> BASE = new ArrayList<>(), SHIP = new ArrayList<>();
    static {
        // Relative to the pillar at (1,1,1). Doorway identifies the rotation.
        for (int x : new int[]{0, 7}) for (int z : new int[]{0, 7}) {
            BASE.add(new Sample(x, 0, z, Block.PILLAR));
            BASE.add(new Sample(x, 1, z, Block.PILLAR));
        }
        for (int n : new int[]{2, 5}) {
            BASE.add(new Sample(0, 1, n, Block.GLASS));
            BASE.add(new Sample(7, 1, n, Block.GLASS));
            BASE.add(new Sample(n, 1, 0, Block.GLASS));
            BASE.add(new Sample(n, 1, 7, Block.BRICKS));
        }
        BASE.add(new Sample(3, -1, 3, Block.PURPUR));
        BASE.add(new Sample(0, -1, 0, Block.PURPUR));
        BASE.add(new Sample(0, 2, 2, Block.GLASS));
        // Relative to obsidian (5,2,12): two 13-block keel rows, central purpur.
        for (int z = 0; z <= 12; z++) {
            SHIP.add(new Sample(0, 0, z, Block.OBSIDIAN));
            SHIP.add(new Sample(2, 0, z, Block.OBSIDIAN));
        }
        SHIP.add(new Sample(1, 0, 0, Block.PILLAR));
        SHIP.add(new Sample(1, 0, 12, Block.PILLAR));
        SHIP.add(new Sample(1, 1, -5, Block.PURPUR));
    }
    static boolean matches(Pos p, int rotation, List<Sample> samples, Reader reader) {
        for (var s : samples) {
            var q = p.offset(s.x, s.y, s.z, rotation);
            if (reader.get(q.x, q.y, q.z) != s.block) return false;
        }
        return true;
    }
    public static Pos city(Pos pillar, Reader reader) {
        for (int r = 0; r < 4; r++) if (matches(pillar, r, BASE, reader)) {
            // Center of the 8x8 floor, rounded consistently for each rotation.
            var a = pillar.offset(7, 0, 7, r);
            return new Pos(Math.floorDiv(pillar.x + a.x, 2), pillar.y - 1, Math.floorDiv(pillar.z + a.z, 2));
        }
        return null;
    }
    public static Pos ship(Pos obsidian, Reader reader) {
        for (int r = 0; r < 4; r++) if (matches(obsidian, r, SHIP, reader))
            return obsidian.offset(1, 3, -5, r); // Elytra item-frame attachment cell.
        return null;
    }
}
