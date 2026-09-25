package dev.bedrockscout;

import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** Scans at most one 16x16 candidate area per client tick. */
final class BedrockScanner implements BedrockPattern.Reader {
    private final ClientLevel level;
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>(), scanned = new HashSet<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    BedrockScanner(ClientLevel level) { this.level = level; }

    void enqueue(int chunkX, int chunkZ, boolean rescan) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        if (rescan) scanned.remove(key);
        if (!scanned.contains(key) && queued.add(key)) queue.addLast(key);
    }
    void enqueueAround(int chunkX, int chunkZ, int radius, boolean rescan) {
        for (int x = chunkX - radius; x <= chunkX + radius; x++)
            for (int z = chunkZ - radius; z <= chunkZ + radius; z++)
                if (level.hasChunk(x, z)) enqueue(x, z, rescan);
    }
    void chunkLoaded(int chunkX, int chunkZ) {
        // A pattern centered in any neighboring chunk may cross this new boundary.
        for (int x = chunkX - 1; x <= chunkX + 1; x++)
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++)
                if (level.hasChunk(x, z)) enqueue(x, z, true);
    }
    List<BedrockStore.Entry> advance(BedrockStore store) {
        while (!queue.isEmpty()) {
            long key = queue.removeFirst(); queued.remove(key);
            int chunkX = ChunkPos.getX(key), chunkZ = ChunkPos.getZ(key);
            if (!level.hasChunk(chunkX, chunkZ)) continue;
            scanned.add(key);
            var found = new ArrayList<BedrockStore.Entry>();
            int minX = chunkX * 16, minZ = chunkZ * 16;
            for (int x = minX; x < minX + 16; x++) {
                for (int z = minZ; z < minZ + 16; z++) {
                    for (int y = BedrockPattern.MIN_Y; y <= BedrockPattern.MAX_Y; y++) {
                        if (!BedrockPattern.matches(x, y, z, this)) continue;
                        var entry = store.discover(x, y, z, System.currentTimeMillis());
                        if (entry != null) found.add(entry);
                        break; // Prefer the lowest valid platform at this X/Z center.
                    }
                }
            }
            return found;
        }
        return List.of();
    }
    int pending() { return queue.size(); }
    @Override public boolean loaded(int x, int z) { return level.hasChunk(x >> 4, z >> 4); }
    @Override public boolean bedrock(int x, int y, int z) {
        return level.getBlockState(cursor.set(x, y, z)).is(Blocks.BEDROCK);
    }
}
