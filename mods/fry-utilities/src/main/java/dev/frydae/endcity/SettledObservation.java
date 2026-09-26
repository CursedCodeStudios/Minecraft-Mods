package dev.frydae.endcity;

/** Five uninterrupted seconds of suitable client coverage are required for absence. */
final class SettledObservation {
    private int ticks;
    boolean advance(boolean covered, boolean present) {
        ticks = covered && !present ? Math.min(100, ticks + 1) : 0;
        return ticks == 100;
    }
}
