package dev.monumentscout;

/** Partial visibility can raise a count, but a reduction needs settled full coverage. */
final class ElderObservation {
    private int candidate = -1, ticks;
    void observe(boolean covered, int count) {
        if (!covered) { candidate = -1; ticks = 0; return; }
        if (candidate != count) { candidate = count; ticks = 0; }
        ticks = Math.min(100, ticks + 1);
    }
    int confirmedCount() { return ticks >= 100 ? candidate : -1; }
}
