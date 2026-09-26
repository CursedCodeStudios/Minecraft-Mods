package dev.frydae.monument;

/** Vanilla footprint centered between the four blocks just northwest of the anchor. */
public record MonumentBounds(int centerX, int centerZ) {
    public static final int WIDTH = 58;
    public static final int MIN_Y = 39;
    public static final int MAX_Y = 63;
    public static final int VOLUME = WIDTH * WIDTH * (MAX_Y - MIN_Y + 1);
    public MonumentBounds {
        if (Math.abs((long) centerX) > 29_999_900 || Math.abs((long) centerZ) > 29_999_900)
            throw new IllegalArgumentException("Monument outside supported world bounds");
    }
    public int minX() { return centerX - 29; }
    public int maxX() { return centerX + 28; }
    public int minZ() { return centerZ - 29; }
    public int maxZ() { return centerZ + 28; }
    public String key() { return centerX + "," + centerZ; }
    public boolean containsChunk(int x, int z) {
        return x >= (minX() >> 4) && x <= (maxX() >> 4)
            && z >= (minZ() >> 4) && z <= (maxZ() >> 4);
    }
    public boolean containsElderChunk(int x, int z) {
        return x >= ((minX() - 16) >> 4) && x <= ((maxX() + 16) >> 4)
            && z >= ((minZ() - 16) >> 4) && z <= ((maxZ() + 16) >> 4);
    }
    public boolean containsElder(double x, double y, double z) {
        return x >= minX() - 16 && x < maxX() + 17
            && z >= minZ() - 16 && z < maxZ() + 17 && y >= MIN_Y - 16 && y < MAX_Y + 17;
    }
    public boolean canSurveyElders(double x, double y, double z) {
        return Math.abs(x - (centerX - 0.5)) <= 16
            && Math.abs(z - (centerZ - 0.5)) <= 16 && y >= 30 && y <= 80;
    }
    public double distanceSquared(double x, double z) {
        return (centerX - x) * (centerX - x) + (centerZ - z) * (centerZ - z);
    }
}
