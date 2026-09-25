package dev.nautilusscout.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds a render-only outline without changing entity flags or status effects. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void nautilusscout$highlightShellCarrier(Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValueZ() && entity instanceof Drowned drowned && drowned.isAlive()
            && (drowned.getMainHandItem().is(Items.NAUTILUS_SHELL)
                || drowned.getOffhandItem().is(Items.NAUTILUS_SHELL))) {
            result.setReturnValue(true);
        }
    }
}
