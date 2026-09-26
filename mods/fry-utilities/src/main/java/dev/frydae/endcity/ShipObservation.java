package dev.frydae.endcity;

import dev.frydae.endcity.CitySignature.Pos;

/** Frame position is offset toward its backing wall from the display cell center. */
final class ShipObservation {
    static boolean isDisplayFrame(Pos cell, double x, double y, double z) {
        return Math.abs(x - (cell.x() + .5)) < .75 && Math.abs(y - (cell.y() + .5)) < .75
            && Math.abs(z - (cell.z() + .5)) < .75;
    }
}
