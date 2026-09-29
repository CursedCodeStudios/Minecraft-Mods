package dev.frydae.utilities.mixin;

import dev.frydae.utilities.course.ElytraCourse;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow @Final private SimpleGizmoCollector renderThreadGizmos;

    @Inject(method = "finalizeGizmoCollection", at = @At("HEAD"))
    private void fryutilities$drawElytraCourse(CallbackInfo callback) {
        try (var ignored = Gizmos.withCollector(renderThreadGizmos)) {
            ElytraCourse.draw();
        }
    }
}
