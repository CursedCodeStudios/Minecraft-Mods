package dev.seedscout;

import com.github.netherbedrockcracker.Block;
import com.github.netherbedrockcracker.NetherBedrockCracker;
import com.github.netherbedrockcracker.VecI64;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;

final class NativeCracker {
    private NativeCracker() { }

    static long[] crack(List<BedrockObservation> observations, int threads) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment blocks = Block.allocateArray(observations.size(), arena);
            for (int i = 0; i < observations.size(); i++) {
                var observation = observations.get(i);
                MemorySegment block = Block.asSlice(blocks, i);
                Block.x(block, observation.x());
                Block.y(block, observation.y());
                Block.z(block, observation.z());
                Block.block_type(block, NetherBedrockCracker.BEDROCK());
            }

            MemorySegment vector = null;
            try {
                vector = NetherBedrockCracker.crack(arena, blocks, observations.size(), threads,
                    NetherBedrockCracker.Normal(), NetherBedrockCracker.StructureSeed());
                long length = VecI64.len(vector);
                return VecI64.ptr(vector).reinterpret(length * NetherBedrockCracker.C_LONG_LONG.byteSize())
                    .toArray(NetherBedrockCracker.C_LONG_LONG);
            } finally {
                if (vector != null) NetherBedrockCracker.free_vec(vector);
            }
        }
    }
}
