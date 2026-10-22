package dev.frydae.nostrip;

import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoStripTransformsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void matchesNativeConditionalTransformAndLeavesOtherBlocksAlone() {
        var strip = BlockTransformer.BlockTransformData.builder(BlockPredicate.matchesBlocks(Blocks.POPLAR_LOG),
            Blocks.STRIPPED_POPLAR_LOG).build();
        var weathered = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:weathered_copper"));
        var exposed = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:exposed_copper"));
        var scrape = BlockTransformer.BlockTransformData.builder(BlockPredicate.matchesBlocks(weathered), exposed).build();
        var transformer = new BlockTransformer(List.of(strip, scrape));
        assertEquals(Blocks.STRIPPED_POPLAR_LOG.defaultBlockState(), NoStripTransforms.firstResult(transformer,
            level(Blocks.POPLAR_LOG.defaultBlockState()), BlockPos.ZERO, Direction.UP));
        assertEquals(exposed.defaultBlockState(), NoStripTransforms.firstResult(transformer,
            level(weathered.defaultBlockState()), BlockPos.ZERO, Direction.UP));
        assertNull(NoStripTransforms.firstResult(transformer, level(Blocks.STONE.defaultBlockState()),
            BlockPos.ZERO, Direction.UP));
    }
    @Test void pathPreviewHonorsFaceAndAirAboveAndDoesNotTouchWorldRandom() {
        var path = BlockTransformer.BlockTransformData.builder(BlockPredicate.allOf(
            BlockPredicate.matchesBlocks(Blocks.GRASS_BLOCK), BlockPredicate.matchesBlocks(BlockPos.ZERO.above(), List.of(Blocks.AIR))),
            Blocks.DIRT_PATH).disallowedFaces(List.of(Direction.DOWN)).build();
        var transformer = new BlockTransformer(List.of(path));
        assertEquals(Blocks.DIRT_PATH.defaultBlockState(), NoStripTransforms.firstResult(transformer,
            level(Blocks.GRASS_BLOCK.defaultBlockState()), BlockPos.ZERO, Direction.UP));
        assertNull(NoStripTransforms.firstResult(transformer, level(Blocks.GRASS_BLOCK.defaultBlockState()),
            BlockPos.ZERO, Direction.DOWN));
        assertNull(NoStripTransforms.firstResult(transformer, level(Blocks.GRASS_BLOCK.defaultBlockState(), false),
            BlockPos.ZERO, Direction.UP));
    }
    private static LevelAccessor level(net.minecraft.world.level.block.state.BlockState state) { return level(state, true); }
    private static LevelAccessor level(net.minecraft.world.level.block.state.BlockState state, boolean airAbove) {
        return (LevelAccessor)Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(), new Class<?>[]{LevelAccessor.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getBlockState")) return BlockPos.ZERO.equals(args[0]) ? state
                    : (airAbove ? Blocks.AIR : Blocks.STONE).defaultBlockState();
                throw new AssertionError("Preview must not use world mutations or world RNG: " + method.getName());
            });
    }
}
