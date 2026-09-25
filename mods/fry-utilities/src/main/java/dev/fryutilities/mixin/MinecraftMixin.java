package dev.fryutilities.mixin;

import dev.fryutilities.FryUtilities;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void fryutilities$highlightFavoriteTrader(Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValueZ() && FryUtilities.shouldHighlight(entity)) result.setReturnValue(true);
    }
}
