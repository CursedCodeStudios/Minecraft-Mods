package dev.frydae.nostrip;

import com.mojang.blaze3d.platform.InputConstants;
import dev.frydae.utilities.FryUtilities;
import dev.frydae.utilities.mixin.AxeItemAccessor;
import dev.frydae.utilities.mixin.ShovelItemAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
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
            "key.nostrip.togglestrip", InputConstants.Type.KEYSYM, InputConstants.KEY_Y, category));
        UseBlockCallback.EVENT.register(NoStrip::useBlock);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.consumeClick()) toggleProtection();
        });
    }

    private static InteractionResult useBlock(Player player, Level level,
        net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() || !FryUtilities.config().noStripProtection()) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        BlockState state = level.getBlockState(hit.getBlockPos());
        boolean prevented = stack.getItem() instanceof ShovelItem
            ? isShovelPathAttempt(level, state, hit.getBlockPos(), hit.getDirection())
            : preventsTransformation(stack, state);
        if (!prevented) return InteractionResult.PASS;
        showPrevented(player);
        return InteractionResult.FAIL;
    }

    static boolean preventsTransformation(ItemStack stack, BlockState state) {
        var block = state.getBlock();
        if (stack.getItem() instanceof AxeItem)
            return AxeItemAccessor.fryutilities$getStrippables().containsKey(block)
                || WeatheringCopper.getPrevious(state).isPresent()
                || HoneycombItem.WAX_OFF_BY_BLOCK.get().containsKey(block);
        return false;
    }

    /** Exact-method fallback for shovel path creation if another callback bypasses the general hook. */
    public static boolean preventShovelPath(UseOnContext context) {
        if (!FryUtilities.config().noStripProtection()
            || !(context.getItemInHand().getItem() instanceof ShovelItem)) return false;
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!isShovelPathAttempt(level, level.getBlockState(pos), pos, context.getClickedFace())) return false;
        if (level.isClientSide() && context.getPlayer() != null) showPrevented(context.getPlayer());
        return true;
    }

    private static boolean isShovelPathAttempt(Level level, BlockState state, BlockPos pos, Direction face) {
        return face != Direction.DOWN
            && level.getBlockState(pos.above()).isAir()
            && ShovelItemAccessor.fryutilities$getFlattenables().containsKey(state.getBlock())
            && SchematicPathPolicy.shouldBlock(schematicExpectation(pos));
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
