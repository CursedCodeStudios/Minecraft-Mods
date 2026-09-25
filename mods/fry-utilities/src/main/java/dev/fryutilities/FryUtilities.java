package dev.fryutilities;

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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.timeline.Timelines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class FryUtilities implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("fryutilities");
    private static TradingStore store;
    private static String context = "";
    private static UUID pendingVillager, activeVillager;
    private static long pendingAt, ticks, retryAt;
    private static boolean merchantOpen;

    @Override public void onInitializeClient() {
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
        say((added ? "Favorited" : "Unfavorited") + " villager trade " + (index + 1) + ".");
        save(); return true;
    }

    public static boolean isFavorite(int index) {
        return store != null && activeVillager != null && store.isFavorite(activeVillager, index);
    }

    public static boolean shouldHighlight(Entity entity) {
        var player = Minecraft.getInstance().player;
        return store != null && player != null && entity instanceof Villager villager && villager.isAlive()
            && player.distanceToSqr(villager) <= 400.0 && store.hasAvailableFavorite(villager.getUUID());
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
        int estimates = store == null ? 0 : store.estimatedVillagerCount();
        say(favorites + " favorite trades across " + villagers
            + " villagers; " + estimates + " villager highlights currently use workstation restock estimates. "
            + "Middle-click a trade row to toggle it; Shift-click trades repeatedly; Ctrl-click uses vanilla behavior.");
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
