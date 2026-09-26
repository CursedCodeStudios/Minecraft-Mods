package dev.frydae.utilities;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.timeline.Timelines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class FryUtilities implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("dev.frydae.utilities");
    private static final int FAVORITE_TRADE_COLOR = 0xFF00FF00;
    private static final int NAUTILUS_SHELL_COLOR = 0xFFCA7548;
    private static final int TRIDENT_COLOR = 0xFF589D8E;
    private static final java.nio.file.Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir()
        .resolve("fry-utilities").resolve("settings.json");
    private static FryUtilitiesConfig config = FryUtilitiesConfig.defaults();
    private static TradingStore store;
    private static String context = "";
    private static UUID pendingVillager, activeVillager;
    private static long pendingAt, ticks, retryAt;
    private static boolean merchantOpen;

    @Override public void onInitializeClient() {
        try { config = FryUtilitiesConfig.load(CONFIG_FILE); }
        catch (IOException ex) {
            LOG.error("Cannot open Fry Utilities settings", ex);
        }
        ClientTickEvents.END_CLIENT_TICK.register(FryUtilities::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            literal("fryutilities").executes(command -> { status(); return 1; })
        ));
    }

    private static void tick(Minecraft client) {
        updateContext(client);
        if (store == null) return;
        if (client.gui.screen() instanceof MerchantScreen screen) {
            observe(screen.getMenu().getOffers()); merchantOpen = true;
        } else if (merchantOpen) {
            activeVillager = null; merchantOpen = false;
        }
        if (++ticks % 100 == 0) save();
    }

    private static void updateContext(Minecraft client) {
        if (client.level == null || client.player == null) {
            if (!context.isEmpty()) { save(); reset(); }
            return;
        }
        String world;
        if (client.getSingleplayerServer() != null)
            world = "local:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        else if (client.getCurrentServer() != null) world = "server:" + client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        else return;
        String next = world + "\n" + client.level.dimension().identifier();
        if (next.equals(context)) return;
        save(); reset(); context = next;
        try {
            store = new TradingStore(FabricLoader.getInstance().getConfigDir().resolve("fry-utilities"),
                world, client.level.dimension().identifier().toString());
        } catch (IOException ex) {
            LOG.error("Cannot open villager favorites", ex);
            say("Cannot open saved villager favorites. See latest.log.");
        }
    }

    public static void selectVillager(Villager villager) {
        pendingVillager = villager.getUUID(); pendingAt = System.currentTimeMillis();
    }

    public static void openTradeScreen() {
        activeVillager = System.currentTimeMillis() - pendingAt <= 5000 ? pendingVillager : null;
        merchantOpen = true;
    }

    public static void observe(MerchantOffers offers) {
        var client = Minecraft.getInstance();
        if (store != null && activeVillager != null && client.level != null)
            store.observe(activeVillager, offers, restockPeriod(client), client.level.getGameTime());
    }

    public static boolean toggleFavorite(int index, MerchantOffers offers) {
        if (store == null || activeVillager == null || index < 0 || index >= offers.size()) return false;
        boolean added = store.toggle(activeVillager, index, !offers.get(index).isOutOfStock());
        var client = Minecraft.getInstance();
        if (client.level != null)
            store.observe(activeVillager, offers, restockPeriod(client), client.level.getGameTime());
        say((added ? "Favorited" : "Unfavorited") + " villager trade " + (index + 1) + ".");
        save(); return true;
    }

    public static boolean isFavorite(int index) {
        return store != null && activeVillager != null && store.isFavorite(activeVillager, index);
    }

    public static boolean toggleHighlightFavorite(int index) {
        if (store == null || activeVillager == null) return false;
        if (!store.isFavorite(activeVillager, index)) {
            say("Star the trade before marking it for villager highlighting.");
            return true;
        }
        boolean added = store.toggleHighlight(activeVillager, index);
        say((added ? "Marked" : "Unmarked") + " trade " + (index + 1)
            + " for villager highlighting.");
        save();
        return true;
    }

    public static boolean isHighlightFavorite(int index) {
        return store != null && activeVillager != null && store.isHighlightFavorite(activeVillager, index);
    }

    public static boolean isHighlightFavoriteAvailable(int index) {
        return store != null && activeVillager != null && store.isHighlightAvailable(activeVillager, index);
    }

    public static boolean shouldHighlight(Entity entity) {
        return outlineColor(entity) != 0;
    }

    public static int outlineColor(Entity entity) {
        var player = Minecraft.getInstance().player;
        if (config.villagerHighlights() && store != null && player != null
            && entity instanceof Villager villager && villager.isAlive()
            && player.distanceToSqr(villager) <= config.villagerRangeSquared()
            && store.hasAvailableFavorite(villager.getUUID()))
            return FAVORITE_TRADE_COLOR;
        if (entity instanceof Drowned drowned && drowned.isAlive()) {
            int mainHand = equipmentColor(drowned.getMainHandItem());
            return mainHand != 0 ? mainHand : equipmentColor(drowned.getOffhandItem());
        }
        return 0;
    }

    public static Component favoriteBookLabel(Entity entity) {
        var player = Minecraft.getInstance().player;
        if (!config.enchantedBookLabels() || store == null || player == null
            || !(entity instanceof Villager villager) || !villager.isAlive()
            || player.distanceToSqr(villager) > config.villagerRangeSquared()) return null;
        String books = store.favoriteBookLabel(villager.getUUID());
        return books == null ? null : Component.literal("Book: " + books).withStyle(ChatFormatting.AQUA);
    }

    private static int equipmentColor(ItemStack stack) {
        if (config.nautilusHighlights() && stack.is(Items.NAUTILUS_SHELL)) return NAUTILUS_SHELL_COLOR;
        if (config.tridentHighlights() && stack.is(Items.TRIDENT)) return TRIDENT_COLOR;
        return 0;
    }

    public static FryUtilitiesConfig config() { return config; }

    public static void applyConfig(FryUtilitiesConfig next) throws IOException {
        next.save(CONFIG_FILE);
        config = next.copy();
    }

    public static void reportConfigSaveFailure(IOException ex) {
        LOG.error("Cannot save Fry Utilities settings", ex);
        say("Cannot save settings. See latest.log.");
    }

    public static void hearWorkSound(SoundEvent sound, SoundSource source, double x, double y, double z) {
        var client = Minecraft.getInstance();
        if (store == null || client.level == null || source != SoundSource.NEUTRAL) return;
        Villager closest = null;
        double closestDistance = 2.25;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Villager villager) || !villager.isAlive()
                || !store.hasUnavailableFavorite(villager.getUUID())) continue;
            SoundEvent workSound = villager.getVillagerData().profession().value().workSound();
            if (workSound == null || !workSound.equals(sound)) continue;
            double distance = villager.distanceToSqr(x, y, z);
            if (distance < closestDistance) {
                closest = villager;
                closestDistance = distance;
            }
        }
        if (closest != null && store.markProbablyRestocked(closest.getUUID(), restockPeriod(client),
            client.level.getGameTime())) save();
    }

    private static void status() {
        int villagers = store == null ? 0 : store.villagerCount();
        int favorites = store == null ? 0 : store.favoriteCount();
        int highlights = store == null ? 0 : store.highlightFavoriteCount();
        int estimates = store == null ? 0 : store.estimatedVillagerCount();
        say(favorites + " favorite trades across " + villagers
            + " villagers; " + highlights + " are marked for villager highlighting; " + estimates
            + " villager highlights currently use workstation restock estimates. Middle-click a trade row to star it; "
            + "Shift-middle-click a starred row to mark it for highlighting; Shift-click trades repeatedly; "
            + "Ctrl-click uses vanilla behavior.");
    }

    private static int restockPeriod(Minecraft client) {
        if (client.level == null) return 0;
        return client.level.registryAccess().get(Timelines.OVERWORLD_DAY)
            .map(holder -> holder.value().getPeriodCount(client.level.clockManager())).orElse(0);
    }

    private static void save() {
        if (store == null || System.currentTimeMillis() < retryAt) return;
        try { store.save(); retryAt = 0; }
        catch (IOException ex) {
            LOG.error("Cannot save villager favorites", ex);
            if (retryAt == 0) say("Cannot save villager favorites; retrying. See latest.log.");
            retryAt = System.currentTimeMillis() + 5000;
        }
    }

    private static void reset() {
        store = null; context = ""; pendingVillager = null; activeVillager = null;
        pendingAt = 0; ticks = 0; retryAt = 0; merchantOpen = false;
    }

    private static void say(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[Fry Utilities] " + text));
    }
}
