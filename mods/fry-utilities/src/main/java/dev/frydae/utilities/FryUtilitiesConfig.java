package dev.frydae.utilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FryUtilitiesConfig {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCHEMA = 1;
    public static final int DEFAULT_RANGE = 20;

    private boolean villagerHighlights;
    private boolean enchantedBookLabels;
    private boolean nautilusHighlights;
    private boolean tridentHighlights;
    private int villagerRange;

    private FryUtilitiesConfig(boolean villagerHighlights, boolean enchantedBookLabels,
        boolean nautilusHighlights, boolean tridentHighlights, int villagerRange) {
        this.villagerHighlights = villagerHighlights;
        this.enchantedBookLabels = enchantedBookLabels;
        this.nautilusHighlights = nautilusHighlights;
        this.tridentHighlights = tridentHighlights;
        this.villagerRange = clampRange(villagerRange);
    }

    public static FryUtilitiesConfig defaults() {
        return new FryUtilitiesConfig(true, true, true, true, DEFAULT_RANGE);
    }

    public static FryUtilitiesConfig load(Path file) throws IOException {
        if (!Files.exists(file)) return defaults();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!root.has("schema") || root.get("schema").getAsInt() != SCHEMA)
                throw new IllegalArgumentException("Unsupported schema");
            return new FryUtilitiesConfig(
                readBoolean(root, "villagerHighlights", true),
                readBoolean(root, "enchantedBookLabels", true),
                readBoolean(root, "nautilusHighlights", true),
                readBoolean(root, "tridentHighlights", true),
                readInt(root, "villagerRange", DEFAULT_RANGE));
        } catch (RuntimeException ex) {
            throw new IOException("Cannot read " + file + "; original retained", ex);
        }
    }

    public void save(Path file) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.addProperty("villagerHighlights", villagerHighlights);
        root.addProperty("enchantedBookLabels", enchantedBookLabels);
        root.addProperty("nautilusHighlights", nautilusHighlights);
        root.addProperty("tridentHighlights", tridentHighlights);
        root.addProperty("villagerRange", villagerRange);
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public FryUtilitiesConfig copy() {
        return new FryUtilitiesConfig(villagerHighlights, enchantedBookLabels,
            nautilusHighlights, tridentHighlights, villagerRange);
    }

    public boolean villagerHighlights() { return villagerHighlights; }
    public boolean enchantedBookLabels() { return enchantedBookLabels; }
    public boolean nautilusHighlights() { return nautilusHighlights; }
    public boolean tridentHighlights() { return tridentHighlights; }
    public int villagerRange() { return villagerRange; }
    public double villagerRangeSquared() { return (double)villagerRange * villagerRange; }

    void setVillagerHighlights(boolean value) { villagerHighlights = value; }
    void setEnchantedBookLabels(boolean value) { enchantedBookLabels = value; }
    void setNautilusHighlights(boolean value) { nautilusHighlights = value; }
    void setTridentHighlights(boolean value) { tridentHighlights = value; }
    void setVillagerRange(int value) { villagerRange = clampRange(value); }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsBoolean() : fallback;
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsInt() : fallback;
    }

    private static int clampRange(int value) { return Math.max(4, Math.min(64, value)); }
}
