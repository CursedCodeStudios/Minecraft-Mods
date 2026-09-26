package dev.frydae.endcity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.frydae.endcity.CitySignature.Pos;

class SurveyCoverageTest {
    private final CityStore.City city = new CityStore.City(new Pos(-1, 64, -1), false);
    private SurveyCoverage allChunks() {
        var coverage = new SurveyCoverage();
        for (int x = -15; x <= 13; x++) for (int z = -15; z <= 13; z++) coverage.scannedChunk(x, z);
        return coverage;
    }
    @Test void unfinishedMissingAndUnloadedSweepsCannotClaimAbsence() {
        var unfinished = allChunks(); assertFalse(unfinished.covers(city));
        unfinished.finish(); assertTrue(unfinished.covers(city));
        unfinished.invalidate(); assertFalse(unfinished.covers(city));
        var missing = new SurveyCoverage();
        for (int x = -15; x <= 13; x++) for (int z = -15; z <= 13; z++)
            if (x != -1 || z != -1) missing.scannedChunk(x, z);
        missing.finish(); assertFalse(missing.covers(city));
    }
    @Test void loadedBlocksAloneCannotProveEntityCoverage() {
        var c = allChunks(); c.finish();
        assertFalse(c.entityCoverage(city, 0, 80, 0));
        c.structureSection(new Pos(-16, 64, -16));
        assertTrue(c.entityCoverage(city, 0, 80, 0));
        assertFalse(c.entityCoverage(city, 100, 80, 0));
        assertFalse(c.entityCoverage(city, 0, 180, 0));
    }
    @Test void distantTowerOrShipPreventsFalseZeroForALargeCity() {
        var c = allChunks(); c.finish(); c.structureSection(new Pos(-16, 64, -16));
        assertTrue(c.entityCoverage(city, 0, 80, 0));
        c.structureSection(new Pos(160, 128, 0));
        assertFalse(c.entityCoverage(city, 0, 80, 0));
    }
    @Test void unrelatedStructuresOutsideSurveyAreaDoNotBlockCoverage() {
        var c = allChunks(); c.finish(); c.structureSection(new Pos(-16, 64, -16));
        c.structureSection(new Pos(224, 128, 0));
        assertTrue(c.entityCoverage(city, 0, 80, 0));
    }
}
