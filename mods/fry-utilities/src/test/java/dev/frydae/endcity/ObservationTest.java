package dev.frydae.endcity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.frydae.endcity.CitySignature.Pos;

class ObservationTest {
    @Test void absenceRequiresFiveUninterruptedSecondsAndResetsOnLostCoverageOrSighting() {
        var quiet = new SettledObservation();
        for (int i = 0; i < 99; i++) assertFalse(quiet.advance(true, false));
        assertFalse(quiet.advance(false, false));
        for (int i = 0; i < 99; i++) assertFalse(quiet.advance(true, false));
        assertFalse(quiet.advance(true, true));
        for (int i = 0; i < 99; i++) assertFalse(quiet.advance(true, false));
        assertTrue(quiet.advance(true, false));
        assertFalse(quiet.advance(false, false));
    }
    @Test void checksTheDisplayFrameOnEveryWallButRejectsNeighboringFrames() {
        var p = new Pos(-100, 115, 200);
        for (int r = 0; r < 4; r++) {
            double x = p.x() + .5 + (r == 0 ? .46875 : r == 2 ? -.46875 : 0);
            double z = p.z() + .5 + (r == 1 ? .46875 : r == 3 ? -.46875 : 0);
            assertTrue(ShipObservation.isDisplayFrame(p, x, p.y() + .5, z));
        }
        assertFalse(ShipObservation.isDisplayFrame(p, p.x() + 1.5, p.y() + .5, p.z() + .5));
        assertFalse(ShipObservation.isDisplayFrame(p, p.x() + .5, p.y() + 1.5, p.z() + .5));
    }
}
