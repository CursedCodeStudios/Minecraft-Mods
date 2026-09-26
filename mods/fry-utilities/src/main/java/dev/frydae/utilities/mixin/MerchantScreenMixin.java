package dev.frydae.utilities.mixin;

import dev.frydae.utilities.FastTrade;
import dev.frydae.utilities.FryUtilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static net.minecraft.world.inventory.ContainerInput.QUICK_MOVE;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {
    @Shadow private int shopItem;
    @Shadow private int scrollOff;

    protected MerchantScreenMixin(MerchantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "postButtonClick", at = @At("RETURN"))
    private void fryutilities$fastTrade(CallbackInfo callback) {
        if (Minecraft.getInstance().hasControlDown()) return;
        slotClicked(null, 0, 0, QUICK_MOVE);
        slotClicked(null, 1, 0, QUICK_MOVE);
        if ((Object)this instanceof FastTrade fastTrade) fastTrade.fryutilities$trade(shopItem);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void fryutilities$toggleFavorite(MouseButtonEvent event, boolean doubled,
        CallbackInfoReturnable<Boolean> result) {
        if (event.button() != 2) return;
        int left = (width - imageWidth) / 2, top = (height - imageHeight) / 2;
        if (event.x() < left + 5 || event.x() >= left + 94 || event.y() < top + 18 || event.y() >= top + 158) return;
        int row = (int)(event.y() - top - 18) / 20;
        int index = scrollOff + row;
        boolean handled = Minecraft.getInstance().hasShiftDown()
            ? FryUtilities.toggleHighlightFavorite(index)
            : FryUtilities.toggleFavorite(index, menu.getOffers());
        if (handled) result.setReturnValue(true);
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void fryutilities$drawFavorites(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTick, CallbackInfo callback) {
        int left = (width - imageWidth) / 2, top = (height - imageHeight) / 2;
        int visible = Math.min(7, Math.max(0, menu.getOffers().size() - scrollOff));
        for (int row = 0; row < visible; row++) {
            int index = scrollOff + row;
            if (FryUtilities.isFavorite(index))
                graphics.text(font, "★", left - 7, top + 23 + row * 20, 0xFFFFD700, false);
            if (FryUtilities.isHighlightFavorite(index)) {
                int color = FryUtilities.isHighlightFavoriteAvailable(index) ? 0xFF00FF00 : 0xFFFF5555;
                graphics.text(font, "◆", left - 16, top + 23 + row * 20, color, false);
            }
        }
    }
}
