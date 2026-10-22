package dev.frydae.nostrip;

import com.mojang.blaze3d.platform.InputConstants;
import dev.frydae.utilities.FryUtilities;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-side protection against accidental right-click tool transformations. */
public final class NoStrip {
    private static final Logger LOG = LoggerFactory.getLogger("dev.frydae.nostrip");
    private static final long FEEDBACK_DELAY_MS = 1000;
    private static KeyMapping toggle;
    private static long nextFeedback;
    private static boolean litematicaFailureLogged;

    private NoStrip() { }

    public static void initialize() {
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nostrip", "keys"));
        toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.nostrip.togglestrip", InputConstants.Type.KEYBOARD, InputConstants.KEY_Y, category));
        UseBlockCallback.EVENT.register(NoStrip::useBlock);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.consumeClick()) toggleProtection();
        });
    }

    private static InteractionResult useBlock(Player player, Level level,
        net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() || !FryUtilities.config().noStripProtection()) return InteractionResult.PASS;
        return preventTransformation(new UseOnContext(player, hand, hit)) ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    /** Shared block-use gate and exact-method fallback for the 26.3 transformer API. */
    public static boolean preventTransformation(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide() || !FryUtilities.config().noStripProtection()) return false;
        var transformer = context.getItemInHand().get(DataComponents.BLOCK_TRANSFORMER);
        if (transformer == null || !(transformer.is(BlockTransformers.AXE) || transformer.is(BlockTransformers.SHOVEL)))
            return false;
        BlockPos pos = context.getClickedPos();
        var result = NoStripTransforms.firstResult(transformer.value(), level, pos, context.getClickedFace());
        if (result == null || result.equals(level.getBlockState(pos))) return false;
        boolean prevented = transformer.is(BlockTransformers.AXE)
            || (result.is(Blocks.DIRT_PATH) && SchematicPathPolicy.shouldBlock(schematicExpectation(pos)));
        if (!prevented) return false;
        if (context.getPlayer() != null) showPrevented(context.getPlayer());
        return true;
    }

    private static SchematicPathPolicy.Expectation schematicExpectation(BlockPos pos) {
        if (!FabricLoader.getInstance().isModLoaded("litematica"))
            return SchematicPathPolicy.Expectation.NOT_APPLICABLE;
        try { return LitematicaPathCheck.expectationAt(pos); }
        catch (RuntimeException | LinkageError ex) {
            if (!litematicaFailureLogged) {
                litematicaFailureLogged = true;
                LOG.error("Litematica path check failed; shovel path gating is inactive", ex);
            }
            return SchematicPathPolicy.Expectation.NOT_APPLICABLE;
        }
    }

    private static void toggleProtection() {
        boolean enabled = !FryUtilities.config().noStripProtection();
        if (!FryUtilities.setNoStripProtection(enabled)) return;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) player.sendOverlayMessage(Component.literal(
            "No Strip protection " + (enabled ? "enabled" : "disabled"))
            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static void showPrevented(Player player) {
        if (!FryUtilities.config().noStripFeedback() || System.currentTimeMillis() < nextFeedback) return;
        nextFeedback = System.currentTimeMillis() + FEEDBACK_DELAY_MS;
        player.sendOverlayMessage(Component.literal("No Strip prevented this action. Press ")
            .append(toggle.getTranslatedKeyMessage()).append(" to toggle.").withStyle(ChatFormatting.GREEN));
    }
}
