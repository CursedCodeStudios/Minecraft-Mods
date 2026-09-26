package dev.frydae.utilities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import static net.minecraft.world.inventory.ContainerInput.PICKUP;
import static net.minecraft.world.inventory.ContainerInput.SWAP;

/** Complete in-project replacement for Villager Trading Plus's automatic trade screen. */
public final class UtilityMerchantScreen extends MerchantScreen implements FastTrade {
    public UtilityMerchantScreen(MerchantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override public void fryutilities$trade(int tradeIndex) {
        if (tradeIndex < 0 || tradeIndex >= menu.getOffers().size() || minecraft.player == null) return;
        MerchantOffer offer = menu.getOffers().get(tradeIndex);
        int safeguard = 0;
        while (!offer.isOutOfStock()
            && minecraft.player.containerMenu.getCarried().isEmpty()
            && inputSlotsAreEmpty()
            && hasEnoughItems(offer.getCostA())
            && hasEnoughItems(offer.getCostB())
            && canReceive(offer.getResult())) {
            transact(offer);
            // A normal click performs one trade. Hold Shift to trade until stock,
            // inventory space, or supplied items run out.
            if (!Minecraft.getInstance().hasShiftDown() || ++safeguard > 50) break;
        }
        FryUtilities.observe(menu.getOffers());
    }

    private boolean inputSlotsAreEmpty() {
        return menu.getSlot(0).getItem().isEmpty()
            && menu.getSlot(1).getItem().isEmpty()
            && menu.getSlot(2).getItem().isEmpty();
    }

    private boolean hasEnoughItems(ItemStack wanted) {
        if (wanted.isEmpty()) return true;
        int remaining = wanted.getCount();
        for (int i = menu.slots.size() - 36; i < menu.slots.size(); i++) {
            ItemStack candidate = menu.getSlot(i).getItem();
            if (same(wanted, candidate)) remaining -= candidate.getCount();
            if (remaining <= 0) return true;
        }
        return false;
    }

    private boolean canReceive(ItemStack result) {
        for (int i = menu.slots.size() - 36; i < menu.slots.size(); i++) {
            ItemStack candidate = menu.getSlot(i).getItem();
            if (candidate.isEmpty()) return true;
            if (same(result, candidate) && candidate.getCount() + result.getCount() <= candidate.getMaxStackSize())
                return true;
        }
        return false;
    }

    private void transact(MerchantOffer offer) {
        int putBackA = fillSlot(0, offer.getCostA());
        int putBackB = fillSlot(1, offer.getCostB());
        collectResult(2, offer.getResult(), putBackA, putBackB);
        if (putBackA != -1) { click(0); click(putBackA); }
        if (putBackB != -1) { click(1); click(putBackB); }
        // This out-of-range SWAP is a server no-op used by the original mod to
        // force the client inventory synchronization path after a fast trade.
        slotClicked(null, 0, 99, SWAP);
    }

    private int fillSlot(int slot, ItemStack wanted) {
        if (wanted.isEmpty()) return -1;
        int remaining = wanted.getCount();
        for (int i = menu.slots.size() - 36; i < menu.slots.size(); i++) {
            ItemStack candidate = menu.getSlot(i).getItem();
            if (!same(wanted, candidate)) continue;
            boolean putBack = wanted.getCount() + candidate.getCount() > wanted.getMaxStackSize();
            remaining -= candidate.getCount();
            click(i); click(slot);
            if (putBack) click(i);
            if (remaining <= 0) return remaining < 0 ? i : -1;
        }
        return -1;
    }

    private void collectResult(int slot, ItemStack result, int... forbidden) {
        int remaining = result.getCount();
        click(slot);
        for (int i = menu.slots.size() - 36; i < menu.slots.size(); i++) {
            ItemStack candidate = menu.getSlot(i).getItem();
            if (same(result, candidate) && candidate.getCount() < candidate.getMaxStackSize()) {
                remaining -= candidate.getMaxStackSize() - candidate.getCount();
                click(i);
            }
            if (remaining <= 0) return;
        }
        outer: for (int i = menu.slots.size() - 36; i < menu.slots.size(); i++) {
            for (int blocked : forbidden) if (i == blocked) continue outer;
            if (menu.getSlot(i).getItem().isEmpty()) { click(i); return; }
        }
    }

    private void click(int slot) { slotClicked(null, slot, 0, PICKUP); }

    private static boolean same(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameComponents(a, b);
    }
}
