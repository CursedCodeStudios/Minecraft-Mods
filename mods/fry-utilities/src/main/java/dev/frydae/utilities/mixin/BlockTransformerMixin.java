package dev.frydae.utilities.mixin;

import dev.frydae.nostrip.NoStrip;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockTransformer.class)
public abstract class BlockTransformerMixin {
    @Inject(method = "transformBlock", at = @At("HEAD"), cancellable = true)
    private void fryutilities$preventTransformation(UseOnContext context,
        CallbackInfoReturnable<InteractionResult> callback) {
        if (NoStrip.preventTransformation(context)) callback.setReturnValue(InteractionResult.FAIL);
    }
}
