package dev.fryutilities.mixin;

import dev.fryutilities.FryUtilities;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "interact", at = @At("HEAD"))
    private void fryutilities$captureVillager(Player player, Entity entity, EntityHitResult hit,
        InteractionHand hand, CallbackInfoReturnable<InteractionResult> result) {
        if (entity instanceof Villager villager) FryUtilities.selectVillager(villager);
    }
}
