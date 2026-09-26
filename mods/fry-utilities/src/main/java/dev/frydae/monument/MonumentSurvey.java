package dev.frydae.monument;

/** Bounded work per tick; unloaded data never turns into an empty result. */
final class MonumentSurvey {
    interface Reader {
        boolean allChunksLoaded(MonumentBounds bounds);
        boolean isSponge(int x, int y, int z);
    }
    private final MonumentBounds bounds;
    private int cursor, sponges;
    private boolean invalid;
    MonumentSurvey(MonumentBounds bounds) { this.bounds = bounds; }
    MonumentBounds bounds() { return bounds; }
    void invalidate() { invalid = true; }
    boolean invalid() { return invalid; }
    boolean complete() { return !invalid && cursor == MonumentBounds.VOLUME; }
    int sponges() {
        if (!complete()) throw new IllegalStateException("Survey is incomplete");
        return sponges;
    }
    void advance(Reader reader, int budget) {
        if (invalid || complete()) return;
        if (!reader.allChunksLoaded(bounds)) { invalidate(); return; }
        for (int n = 0; n < budget && cursor < MonumentBounds.VOLUME; n++, cursor++) {
            int x = bounds.minX() + cursor % MonumentBounds.WIDTH;
            int z = bounds.minZ() + (cursor / MonumentBounds.WIDTH) % MonumentBounds.WIDTH;
            int y = MonumentBounds.MIN_Y + cursor / (MonumentBounds.WIDTH * MonumentBounds.WIDTH);
            if (reader.isSponge(x, y, z)) sponges++;
        }
        if (!reader.allChunksLoaded(bounds)) invalidate();
    }
}
