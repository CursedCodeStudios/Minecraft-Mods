package dev.frydae.utilities.mixin;

import dev.frydae.utilities.FryUtilities;
import dev.frydae.utilities.UtilityMerchantScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MenuScreens.class)
public abstract class MenuScreensMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static <T extends AbstractContainerMenu> void fryutilities$replaceMerchantScreen(
        MenuType<T> type, Minecraft client, int containerId, Component title, CallbackInfo callback) {
        if (type != MenuType.MERCHANT || client.player == null) return;
        MerchantMenu menu = MenuType.MERCHANT.create(containerId, client.player.getInventory());
        FryUtilities.openTradeScreen();
        client.player.containerMenu = menu;
        client.gui.setScreen(new UtilityMerchantScreen(menu, client.player.getInventory(), title));
        callback.cancel();
    }
}
