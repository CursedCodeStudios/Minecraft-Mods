package dev.seedscout;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

final class ObservationScanner {
    private static final int[][] OFFSETS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private ObservationScanner() { }

    static List<BedrockObservation> scan(ClientLevel level, int centerChunkX, int centerChunkZ) {
        for (int[] offset : OFFSETS) {
            int chunkX = centerChunkX + offset[0], chunkZ = centerChunkZ + offset[1];
            if (!level.hasChunk(chunkX, chunkZ))
                throw new IllegalStateException("The current chunk and its four neighbors must be loaded.");
        }

        var found = new ArrayList<BedrockObservation>();
        var cursor = new BlockPos.MutableBlockPos();
        for (int[] offset : OFFSETS) {
            int minX = (centerChunkX + offset[0]) << 4;
            int minZ = (centerChunkZ + offset[1]) << 4;
            for (int x = minX; x < minX + 16; x++) {
                for (int z = minZ; z < minZ + 16; z++) {
                    if (level.getBlockState(cursor.set(x, 4, z)).is(Blocks.BEDROCK))
                        found.add(new BedrockObservation(x, 4, z));
                    if (level.getBlockState(cursor.set(x, 123, z)).is(Blocks.BEDROCK))
                        found.add(new BedrockObservation(x, 123, z));
                }
            }
        }
        return List.copyOf(found);
    }
}
