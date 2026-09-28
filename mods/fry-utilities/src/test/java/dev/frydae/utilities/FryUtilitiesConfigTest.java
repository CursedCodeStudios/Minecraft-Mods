package dev.frydae.utilities;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FryUtilitiesConfigTest {
    @TempDir Path folder;

    @Test void defaultsAndRoundTrip() throws Exception {
        Path file = folder.resolve("settings.json");
        var defaults = FryUtilitiesConfig.load(file);
        assertTrue(defaults.villagerHighlights());
        assertTrue(defaults.enchantedBookLabels());
        assertTrue(defaults.nautilusHighlights());
        assertTrue(defaults.tridentHighlights());
        assertTrue(defaults.noStripProtection());
        assertTrue(defaults.noStripFeedback());
        assertTrue(defaults.containerExportButton());
        assertTrue(defaults.slimeScoutEnabled());
        assertTrue(defaults.monumentScoutEnabled());
        assertTrue(defaults.endCityScoutEnabled());
        assertTrue(defaults.slimeScoutChat());
        assertTrue(defaults.monumentScoutChat());
        assertTrue(defaults.endCityScoutChat());
        assertEquals(20, defaults.villagerRange());

        defaults.setVillagerHighlights(false);
        defaults.setEnchantedBookLabels(false);
        defaults.setNautilusHighlights(false);
        defaults.setTridentHighlights(false);
        defaults.setNoStripProtection(false);
        defaults.setNoStripFeedback(false);
        defaults.setContainerExportButton(false);
        defaults.setSlimeScoutEnabled(false);
        defaults.setMonumentScoutEnabled(false);
        defaults.setEndCityScoutEnabled(false);
        defaults.setSlimeScoutChat(false);
        defaults.setMonumentScoutChat(false);
        defaults.setEndCityScoutChat(false);
        defaults.setVillagerRange(48);
        defaults.save(file);

        var loaded = FryUtilitiesConfig.load(file);
        assertFalse(loaded.villagerHighlights());
        assertFalse(loaded.enchantedBookLabels());
        assertFalse(loaded.nautilusHighlights());
        assertFalse(loaded.tridentHighlights());
        assertFalse(loaded.noStripProtection());
        assertFalse(loaded.noStripFeedback());
        assertFalse(loaded.containerExportButton());
        assertFalse(loaded.slimeScoutEnabled());
        assertFalse(loaded.monumentScoutEnabled());
        assertFalse(loaded.endCityScoutEnabled());
        assertFalse(loaded.slimeScoutChat());
        assertFalse(loaded.monumentScoutChat());
        assertFalse(loaded.endCityScoutChat());
        assertEquals(48, loaded.villagerRange());
    }

    @Test void missingSettingsUseDefaultsAndRangeIsClamped() throws Exception {
        Path file = folder.resolve("settings.json");
        Files.writeString(file, "{\"schema\":1,\"villagerRange\":200}");
        var loaded = FryUtilitiesConfig.load(file);
        assertTrue(loaded.villagerHighlights());
        assertTrue(loaded.enchantedBookLabels());
        assertTrue(loaded.noStripProtection());
        assertTrue(loaded.noStripFeedback());
        assertTrue(loaded.containerExportButton());
        assertTrue(loaded.slimeScoutEnabled());
        assertTrue(loaded.monumentScoutEnabled());
        assertTrue(loaded.endCityScoutEnabled());
        assertTrue(loaded.slimeScoutChat());
        assertTrue(loaded.monumentScoutChat());
        assertTrue(loaded.endCityScoutChat());
        assertEquals(64, loaded.villagerRange());
    }

    @Test void corruptConfigIsPreserved() throws Exception {
        Path file = folder.resolve("settings.json");
        Files.writeString(file, "{bad");
        assertThrows(java.io.IOException.class, () -> FryUtilitiesConfig.load(file));
        assertEquals("{bad", Files.readString(file));
    }
}
