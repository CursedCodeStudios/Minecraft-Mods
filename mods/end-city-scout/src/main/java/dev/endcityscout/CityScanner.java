package dev.endcityscout;

import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import dev.endcityscout.CitySignature.Pos;

/** Incremental, loaded-chunk-only template search. One candidate section per tick. */
final class CityScanner implements CitySignature.Reader {
    private static final int CHUNK_RADIUS = 15, WIDTH = CHUNK_RADIUS * 2 + 1;
    private final ClientLevel level;
    private final CityStore store;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final SurveyCoverage coverage = new SurveyCoverage();
    private int originX, originZ, chunkIndex, sectionIndex;
    private boolean complete;
    CityScanner(ClientLevel level, CityStore store, int chunkX, int chunkZ) {
        this.level = level; this.store = store; originX = chunkX - CHUNK_RADIUS; originZ = chunkZ - CHUNK_RADIUS;
    }
    void invalidate() { coverage.invalidate(); }
    boolean complete() { return complete; }
    boolean covers(CityStore.City c) {
        return coverage.covers(c);
    }
    boolean entityCoverage(CityStore.City city, double x, double y, double z) {
        return coverage.entityCoverage(city, x, y, z);
    }
    /** Returns whether any persistent discoveries were added. Empty sections cost no block reads. */
    boolean advance() {
        boolean changed = false;
        for (int skipped = 0; skipped < 256 && chunkIndex < WIDTH * WIDTH; skipped++) {
            int cx = originX + chunkIndex % WIDTH, cz = originZ + chunkIndex / WIDTH;
            if (!level.hasChunk(cx, cz)) { chunkIndex++; sectionIndex = 0; continue; }
            var chunk = level.getChunk(cx, cz);
            if (sectionIndex >= chunk.getSections().length) {
                coverage.scannedChunk(cx, cz); chunkIndex++; sectionIndex = 0; continue;
            }
            int sy = level.getSectionYFromSectionIndex(sectionIndex++) * 16;
            var section = chunk.getSections()[sectionIndex - 1];
            if (!section.maybeHas(s -> s.is(Blocks.PURPUR_BLOCK) || s.is(Blocks.PURPUR_PILLAR) || s.is(Blocks.OBSIDIAN))) continue;
            if (section.maybeHas(s -> s.is(Blocks.PURPUR_BLOCK) || s.is(Blocks.PURPUR_PILLAR)))
                coverage.structureSection(new Pos(cx * 16, sy, cz * 16));
            for (int i = 0; i < 4096; i++) {
                int x = i & 15, z = i >> 4 & 15, y = i >> 8;
                var state = section.getBlockState(x, y, z);
                Pos p = new Pos(cx * 16 + x, sy + y, cz * 16 + z);
                if (state.is(Blocks.PURPUR_PILLAR)) {
                    var center = CitySignature.city(p, this);
                    if (center != null) changed |= store.discover(center, false);
                } else if (state.is(Blocks.OBSIDIAN)) {
                    var frame = CitySignature.ship(p, this);
                    if (frame != null) changed |= store.discoverShip(frame);
                }
            }
            return changed;
        }
        if (chunkIndex >= WIDTH * WIDTH) { complete = true; coverage.finish(); }
        return changed;
    }
    @Override public CitySignature.Block get(int x, int y, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return CitySignature.Block.UNLOADED;
        return classify(level.getBlockState(cursor.set(x, y, z)));
    }
    private static CitySignature.Block classify(BlockState s) {
        if (s.is(Blocks.PURPUR_BLOCK)) return CitySignature.Block.PURPUR;
        if (s.is(Blocks.PURPUR_PILLAR)) return CitySignature.Block.PILLAR;
        if (s.is(Blocks.END_STONE_BRICKS)) return CitySignature.Block.BRICKS;
        if (s.is(Blocks.STAINED_GLASS.magenta())) return CitySignature.Block.GLASS;
        if (s.is(Blocks.OBSIDIAN)) return CitySignature.Block.OBSIDIAN;
        return CitySignature.Block.OTHER;
    }
}
