package dev.fryutilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.world.item.trading.MerchantOffers;

public final class TradingStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final class VillagerData {
        Set<Integer> favorites = new TreeSet<>();
        Map<String, Boolean> available = new TreeMap<>();
        Set<Integer> estimatedAvailable = new TreeSet<>();
        int restockPeriod = Integer.MIN_VALUE;
        int estimatedRestocks;
        long lastEstimatedRestock = Long.MIN_VALUE;
    }
    private static final class Data {
        int schema = 1;
        Map<String, VillagerData> villagers = new TreeMap<>();
    }

    private final Path file;
    private Data data = new Data();
    private boolean dirty;

    public TradingStore(Path folder, String world, String dimension) throws IOException {
        file = folder.resolve(hash(world + "\n" + dimension) + ".json");
        if (!Files.exists(file)) return;
        try {
            data = JSON.fromJson(Files.readString(file), Data.class);
            if (data == null || data.schema != 1 || data.villagers == null) throw new IllegalArgumentException("Unsupported data");
            for (var pair : data.villagers.entrySet()) {
                UUID.fromString(pair.getKey());
                var value = pair.getValue();
                if (value == null || value.favorites == null || value.available == null
                    || value.favorites.stream().anyMatch(index -> index == null || index < 0 || index > 99))
                    throw new IllegalArgumentException("Invalid villager favorite data");
                if (value.estimatedAvailable == null) value.estimatedAvailable = new TreeSet<>();
                if (!value.favorites.containsAll(value.estimatedAvailable)
                    || value.estimatedRestocks < 0 || value.estimatedRestocks > 2)
                    throw new IllegalArgumentException("Invalid villager restock estimate data");
            }
        } catch (RuntimeException ex) { throw new IOException("Cannot read " + file + "; original retained", ex); }
    }

    public boolean toggle(UUID villager, int index, boolean available) {
        var record = data.villagers.computeIfAbsent(villager.toString(), ignored -> new VillagerData());
        boolean added;
        if (record.favorites.remove(index)) {
            record.available.remove(Integer.toString(index)); added = false;
            record.estimatedAvailable.remove(index);
            if (record.favorites.isEmpty()) data.villagers.remove(villager.toString());
        } else {
            record.favorites.add(index); record.available.put(Integer.toString(index), available); added = true;
        }
        dirty = true;
        return added;
    }

    public void observe(UUID villager, MerchantOffers offers, int restockPeriod, long gameTime) {
        var record = data.villagers.get(villager.toString());
        if (record == null) return;
        boolean newlyAvailable = false;
        for (int index : record.favorites) {
            boolean current = index < offers.size() && !offers.get(index).isOutOfStock();
            String key = Integer.toString(index);
            if (current && Boolean.FALSE.equals(record.available.get(key))) newlyAvailable = true;
            if (!Objects.equals(record.available.put(key, current), current)) dirty = true;
            if (record.estimatedAvailable.remove(index)) dirty = true;
        }
        if (newlyAvailable) recordRestock(record, restockPeriod, gameTime);
    }

    public boolean markProbablyRestocked(UUID villager, int restockPeriod, long gameTime) {
        var record = data.villagers.get(villager.toString());
        if (record == null || record.favorites.stream().noneMatch(
            index -> Boolean.FALSE.equals(record.available.get(Integer.toString(index))))) return false;
        resetRestockWindow(record, restockPeriod, gameTime);
        if (record.estimatedRestocks >= 2
            || record.estimatedRestocks > 0 && gameTime - record.lastEstimatedRestock <= 2400) return false;
        boolean changed = false;
        for (int index : record.favorites) {
            String key = Integer.toString(index);
            if (Boolean.FALSE.equals(record.available.get(key))) {
                record.available.put(key, true);
                record.estimatedAvailable.add(index);
                changed = true;
            }
        }
        if (changed) {
            record.estimatedRestocks++;
            record.lastEstimatedRestock = gameTime;
            dirty = true;
        }
        return changed;
    }

    public boolean isFavorite(UUID villager, int index) {
        var record = data.villagers.get(villager.toString());
        return record != null && record.favorites.contains(index);
    }

    public boolean hasAvailableFavorite(UUID villager) {
        var record = data.villagers.get(villager.toString());
        return record != null && record.favorites.stream().anyMatch(index -> Boolean.TRUE.equals(record.available.get(Integer.toString(index))));
    }

    public boolean hasUnavailableFavorite(UUID villager) {
        var record = data.villagers.get(villager.toString());
        return record != null && record.favorites.stream().anyMatch(
            index -> Boolean.FALSE.equals(record.available.get(Integer.toString(index))));
    }

    public int villagerCount() { return data.villagers.size(); }
    public int favoriteCount() { return data.villagers.values().stream().mapToInt(record -> record.favorites.size()).sum(); }
    public int estimatedVillagerCount() {
        return (int)data.villagers.values().stream().filter(record -> !record.estimatedAvailable.isEmpty()).count();
    }

    private static void recordRestock(VillagerData record, int restockPeriod, long gameTime) {
        resetRestockWindow(record, restockPeriod, gameTime);
        if (record.estimatedRestocks < 2) record.estimatedRestocks++;
        record.lastEstimatedRestock = gameTime;
        record.restockPeriod = restockPeriod;
    }

    private static void resetRestockWindow(VillagerData record, int restockPeriod, long gameTime) {
        if ((record.restockPeriod != Integer.MIN_VALUE && restockPeriod > record.restockPeriod)
            || record.lastEstimatedRestock > gameTime
            || (record.estimatedRestocks > 0 && gameTime - record.lastEstimatedRestock > 12000)) {
            record.estimatedRestocks = 0;
            record.lastEstimatedRestock = Long.MIN_VALUE;
        }
        record.restockPeriod = restockPeriod;
    }

    public void save() throws IOException {
        if (!dirty) return;
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(data), StandardCharsets.UTF_8);
        try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        dirty = false;
    }

    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
