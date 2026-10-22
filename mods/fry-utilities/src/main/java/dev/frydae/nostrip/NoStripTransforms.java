package dev.frydae.nostrip;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** Inspect the first applicable native transform without changing the world or its random stream. */
final class NoStripTransforms {
    private NoStripTransforms() {}
    static BlockState firstResult(BlockTransformer transformer, LevelAccessor level, BlockPos pos, Direction face) {
        // Vanilla axe/shovel providers are deterministic. Use a separate RNG for the preview.
        var random = RandomSource.create(0);
        for (var transform : transformer.transforms()) {
            if (transform.disallowedFaces().contains(face)) continue;
            var result = transform.blockStateProvider().value().getOptionalState(level, random, pos);
            if (result != null) return result;
        }
        return null;
    }
}
