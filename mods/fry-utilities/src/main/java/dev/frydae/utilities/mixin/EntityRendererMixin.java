package dev.frydae.utilities.mixin;

import dev.frydae.utilities.FryUtilities;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fryutilities$colorUtilityHighlight(Entity entity, EntityRenderState state,
        float partialTick, CallbackInfo callback) {
        int color = FryUtilities.outlineColor(entity);
        if (color != 0) state.outlineColor = color;
        Component bookLabel = FryUtilities.favoriteBookLabel(entity);
        if (bookLabel != null) {
            state.nameTag = state.nameTag == null ? bookLabel
                : Component.empty().append(state.nameTag).append(" | ").append(bookLabel);
            state.nameTagAttachment = entity.getAttachments().get(
                EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick));
        }
    }
}
