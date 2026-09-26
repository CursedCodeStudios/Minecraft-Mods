package dev.frydae.utilities;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FryUtilitiesConfigScreen extends Screen {
    private static final List<Integer> RANGES = List.of(4, 8, 12, 16, 20, 24, 32, 48, 64);
    private final Screen parent;
    private FryUtilitiesConfig draft;

    public FryUtilitiesConfigScreen(Screen parent) {
        super(Component.literal("Fry Utilities Settings"));
        this.parent = parent;
        draft = FryUtilities.config().copy();
    }

    @Override protected void init() {
        int contentWidth = Math.min(310, width - 24);
        int left = (width - contentWidth) / 2;
        int buttonWidth = Math.min(260, contentWidth);
        int buttonLeft = (width - buttonWidth) / 2;
        int y = Math.max(34, height / 2 - 112);

        addRenderableOnly(new StringWidget(left, y, contentWidth, 20, title, font));
        y += 28;
        addToggle(buttonLeft, y, buttonWidth, "Villager marked-trade highlights", draft.villagerHighlights(),
            draft::setVillagerHighlights);
        y += 24;
        addToggle(buttonLeft, y, buttonWidth, "Enchanted-book labels", draft.enchantedBookLabels(),
            draft::setEnchantedBookLabels);
        y += 24;
        addToggle(buttonLeft, y, buttonWidth, "Nautilus shell highlights", draft.nautilusHighlights(),
            draft::setNautilusHighlights);
        y += 24;
        addToggle(buttonLeft, y, buttonWidth, "Trident highlights", draft.tridentHighlights(),
            draft::setTridentHighlights);
        y += 24;
        addRenderableWidget(CycleButton.builder(value -> Component.literal(value + " blocks"), draft.villagerRange())
            .withValues(RANGES)
            .create(buttonLeft, y, buttonWidth, 20, Component.literal("Villager display range"),
                (button, value) -> draft.setVillagerRange(value)));

        y += 29;
        var description = new MultiLineTextWidget(left, y,
            Component.literal("Outlines require a starred trade also marked with Shift + middle-click."), font)
            .setMaxWidth(contentWidth).setCentered(true).setMaxRows(2);
        addRenderableOnly(description);

        int footerY = Math.min(height - 28, y + description.getHeight() + 14);
        int smallWidth = (buttonWidth - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            draft = FryUtilitiesConfig.defaults();
            rebuildWidgets();
        }).bounds(buttonLeft, footerY, smallWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
            .bounds(buttonLeft + smallWidth + 4, footerY, smallWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> saveAndClose())
            .bounds(buttonLeft + (smallWidth + 4) * 2, footerY, smallWidth, 20).build());
    }

    private void addToggle(int x, int y, int width, String label, boolean value,
        java.util.function.Consumer<Boolean> update) {
        addRenderableWidget(CycleButton.onOffBuilder(value).create(x, y, width, 20,
            Component.literal(label), (button, selected) -> update.accept(selected)));
    }

    private void saveAndClose() {
        try {
            FryUtilities.applyConfig(draft);
            minecraft.gui.setScreen(parent);
        } catch (IOException ex) {
            FryUtilities.reportConfigSaveFailure(ex);
        }
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
