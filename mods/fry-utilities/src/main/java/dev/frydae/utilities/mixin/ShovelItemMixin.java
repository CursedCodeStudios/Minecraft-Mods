package dev.frydae.utilities.mixin;

import dev.frydae.nostrip.NoStrip;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cancels path creation at the shovel itself as a fallback for the Fabric block-use gate. */
@Mixin(ShovelItem.class)
public abstract class ShovelItemMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void fryutilities$preventPathCreation(UseOnContext context,
        CallbackInfoReturnable<InteractionResult> callback) {
        if (NoStrip.preventShovelPath(context)) callback.setReturnValue(InteractionResult.FAIL);
    }
}
