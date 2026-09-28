package dev.frydae.nostrip;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Optional Litematica API calls, isolated so Fry Utilities still loads without Litematica. */
final class LitematicaPathCheck {
    private LitematicaPathCheck() { }

    static SchematicPathPolicy.Expectation expectationAt(BlockPos pos) {
        var manager = DataManager.getSchematicPlacementManager();
        boolean covered = manager.getAllPlacementsTouchingChunk(pos).stream()
            .anyMatch(part -> part.getPlacement().isEnabled() && part.getBox().contains(pos));
        if (!covered) return SchematicPathPolicy.Expectation.NOT_APPLICABLE;
        var schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) return SchematicPathPolicy.Expectation.NOT_APPLICABLE;
        return schematicWorld.getBlockState(pos).is(Blocks.DIRT_PATH)
            ? SchematicPathPolicy.Expectation.EXPECTS_PATH
            : SchematicPathPolicy.Expectation.EXPECTS_OTHER;
    }
}
