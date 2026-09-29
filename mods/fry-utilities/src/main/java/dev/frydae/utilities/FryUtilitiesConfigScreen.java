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
    private enum Page {
        FEATURES("Features"), NO_STRIP("No Strip"), SCOUTS("Scouts"), SCOUT_CHAT("Scout chat"), COURSE("Elytra course");
        private final String label;
        Page(String label) { this.label = label; }
    }
    private final Screen parent;
    private FryUtilitiesConfig draft;
    private Page page = Page.FEATURES;

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
        int top = Math.max(8, (height - 224) / 2);

        addRenderableOnly(new StringWidget(left, top, contentWidth, 20, title, font));
        addRenderableWidget(CycleButton.builder(value -> Component.literal(value.label), page)
            .withValues(List.of(Page.FEATURES, Page.NO_STRIP, Page.SCOUTS, Page.SCOUT_CHAT, Page.COURSE))
            .create(buttonLeft, top + 24, buttonWidth, 20, Component.literal("Settings page"),
                (button, selected) -> { page = selected; rebuildWidgets(); }));

        int y = top + 52;
        if (page == Page.FEATURES) addFeatureSettings(buttonLeft, buttonWidth, y);
        else if (page == Page.NO_STRIP) addNoStripSettings(left, contentWidth, buttonLeft, buttonWidth, y);
        else if (page == Page.SCOUTS) addScoutSettings(left, contentWidth, buttonLeft, buttonWidth, y);
        else if (page == Page.SCOUT_CHAT) addScoutChatSettings(left, contentWidth, buttonLeft, buttonWidth, y);
        else addCourseSettings(left, contentWidth, buttonLeft, buttonWidth, y);

        int footerY = height - 28;
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

    private void addScoutSettings(int left, int contentWidth, int buttonLeft, int buttonWidth, int y) {
        addToggle(buttonLeft, y, buttonWidth, "Slime Scout", draft.slimeScoutEnabled(),
            draft::setSlimeScoutEnabled);
        y += 24;
        addToggle(buttonLeft, y, buttonWidth, "Monument Scout", draft.monumentScoutEnabled(),
            draft::setMonumentScoutEnabled);
        y += 24;
        addToggle(buttonLeft, y, buttonWidth, "End City Scout", draft.endCityScoutEnabled(),
            draft::setEndCityScoutEnabled);
        y += 30;
        var description = new MultiLineTextWidget(left, y,
            Component.literal("Turning a scout off pauses its scanning and observations and hides its Xaero markers. Saved scout records are retained."), font)
            .setMaxWidth(contentWidth).setCentered(true).setMaxRows(4);
        addRenderableOnly(description);
    }

    private void addFeatureSettings(int buttonLeft, int buttonWidth, int y) {
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
        addToggle(buttonLeft, y, buttonWidth, "Container JSON export button", draft.containerExportButton(),
            draft::setContainerExportButton);
        y += 24;
        addRenderableWidget(CycleButton.builder(value -> Component.literal(value + " blocks"), draft.villagerRange())
            .withValues(RANGES)
            .create(buttonLeft, y, buttonWidth, 20, Component.literal("Villager display range"),
                (button, value) -> draft.setVillagerRange(value)));
    }

    private void addScoutChatSettings(int left, int contentWidth, int buttonLeft, int buttonWidth, int y) {
        addToggle(buttonLeft, y, buttonWidth, "Slime Scout chat", draft.slimeScoutChat(),
            draft::setSlimeScoutChat);
        y += 24;
        addToggle(buttonLeft, y, buttonWidth, "Monument Scout chat", draft.monumentScoutChat(),
            draft::setMonumentScoutChat);
        y += 24;
        addToggle(buttonLeft, y, buttonWidth, "End City Scout chat", draft.endCityScoutChat(),
            draft::setEndCityScoutChat);
        y += 30;
        var description = new MultiLineTextWidget(left, y,
            Component.literal("These switches mute automatic scout announcements. Command replies stay visible."), font)
            .setMaxWidth(contentWidth).setCentered(true).setMaxRows(3);
        addRenderableOnly(description);
    }

    private void addCourseSettings(int left, int contentWidth, int buttonLeft, int buttonWidth, int y) {
        addToggle(buttonLeft, y, buttonWidth, "Elytra course recording and line", draft.elytraCourseEnabled(),
            draft::setElytraCourseEnabled);
        y += 34;
        var description = new MultiLineTextWidget(left, y,
            Component.literal("Use /elytracourse create <name> for a named Nether course, then /elytracourse a and b at its endpoints. Learned lines appear within five blocks of an endpoint and stay visible while flying. /elytracourse shows commands."), font)
            .setMaxWidth(contentWidth).setCentered(true).setMaxRows(5);
        addRenderableOnly(description);
    }

    private void addNoStripSettings(int left, int contentWidth, int buttonLeft, int buttonWidth, int y) {
        addToggle(buttonLeft, y, buttonWidth, "Prevent tool transformations", draft.noStripProtection(),
            draft::setNoStripProtection);
        y += 24;
        addToggle(buttonLeft, y, buttonWidth, "Show prevention feedback", draft.noStripFeedback(),
            draft::setNoStripFeedback);
        y += 30;
        var description = new MultiLineTextWidget(left, y,
            Component.literal("Prevents accidental log stripping, copper scraping and unwaxing. With Litematica, it also blocks paths that conflict with an enabled schematic placement. Use the No Strip keybind (Y by default) to toggle protection."), font)
            .setMaxWidth(contentWidth).setCentered(true).setMaxRows(4);
        addRenderableOnly(description);
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
