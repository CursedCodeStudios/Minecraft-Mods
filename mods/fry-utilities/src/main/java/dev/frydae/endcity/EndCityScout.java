package dev.frydae.endcity;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;
import dev.frydae.endcity.CitySignature.Pos;
import dev.frydae.endcity.CityStore.Presence;

public final class EndCityScout implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("dev.frydae.endcity");
    private final Map<String, SettledObservation> quiet = new HashMap<>();
    private ClientLevel level;
    private CityStore store;
    private CityScanner scanning, surveyed;
    private XaeroBridge xaero;
    private String context = "", dimension = "";
    private long tick, surveyedTick, retry;
    private int revision;
    private boolean markers = true;

    @Override public void onInitializeClient() {
        if (FabricLoader.getInstance().isModLoaded("xaerominimap")) xaero = new XaeroBridge();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { retry = 0; save(); if (xaero != null) xaero.clear(); });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (world != level || store == null) return;
            if (scanning != null) scanning.invalidate();
            surveyed = null; quiet.clear();
            store.cities().forEach(c -> { c.fresh = false; c.searchFresh = false; });
            store.ships().forEach(s -> s.fresh = false);
            revision++;
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            literal("endcityscout").executes(c -> { status(); return 1; })
                .then(literal("here").executes(c -> { here(); return 1; }))
                .then(literal("scan").executes(c -> {
                    scanning = null; surveyed = null; quiet.clear();
                    say("New city and ship survey queued."); return 1;
                }))
                .then(literal("markers").executes(c -> {
                    markers = !markers; revision++; say("Markers " + (markers ? "shown" : "hidden")); return 1;
                }))
                .then(literal("mark").then(argument("centerX", IntegerArgumentType.integer(-29_999_700, 29_999_700))
                    .then(argument("centerZ", IntegerArgumentType.integer(-29_999_700, 29_999_700))
                        .executes(c -> {
                            var player = Minecraft.getInstance().player;
                            if (store == null || player == null) { say("Join the End first."); return 0; }
                            boolean added = store.discover(new Pos(IntegerArgumentType.getInteger(c, "centerX"),
                                player.blockPosition().getY(), IntegerArgumentType.getInteger(c, "centerZ")), true);
                            revision++; say(added ? "City recorded; survey pending." : "A city is already recorded at this entrance.");
                            return 1;
                        }))))
        ));
    }

    private void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            if (!context.isEmpty() && save()) reset();
            return;
        }
        String dim = client.level.dimension().identifier().toString();
        String world;
        if (client.getSingleplayerServer() != null)
            world = "local:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        else if (client.getCurrentServer() != null) world = "server:" + client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        else return;
        String next = world + "\n" + dim;
        if (!next.equals(context) || level != client.level) {
            if (!save()) return;
            reset(); context = next; dimension = dim; level = client.level;
            if (!dim.equals("minecraft:the_end")) return;
            try { store = new CityStore(FabricLoader.getInstance().getConfigDir().resolve("end-city-scout"), world, dim); }
            catch (IOException ex) {
                LOG.error("Cannot open End city records", ex);
                say("Cannot open saved cities; tracking disabled for this world. See latest.log.");
            }
        }
        if (store == null) return;
        tick++;
        if (scanning == null) scanning = new CityScanner(level, store, client.player.chunkPosition().x(), client.player.chunkPosition().z());
        int oldCities = store.cities().size(), oldShips = store.ships().size();
        if (scanning.advance()) {
            revision++; surveyed = null;
            if (store.cities().size() > oldCities) say("End city recorded. Use /endcityscout here for its survey.");
            if (store.ships().size() > oldShips) say("End ship found; checking its elytra item frame.");
        }
        if (scanning.complete()) {
            surveyed = scanning; surveyedTick = tick; scanning = null;
            for (var city : store.cities()) if (surveyed.covers(city)) store.searched(city);
            revision++;
        }
        if (tick - surveyedTick > 1200) surveyed = null;
        observe(client);
        if (tick % 100 == 0) save();
        if (xaero != null && tick % 10 == 0) {
            try { xaero.sync(store, dimension, markers, revision); }
            catch (RuntimeException | LinkageError ex) {
                LOG.error("Xaero integration failed", ex);
                try { xaero.clear(); } catch (RuntimeException | LinkageError ignored) { }
                xaero = null; say("Xaero unavailable; city tracking continues. See latest.log.");
            }
        }
    }
    private void observe(Minecraft client) {
        var counts = new HashMap<CityStore.City, Integer>();
        var frames = new ArrayList<ItemFrame>();
        for (var e : level.entitiesForRendering()) {
            if (!e.isAlive()) continue;
            if (e.getType() == EntityTypes.SHULKER) {
                var owner = store.owner(e.getX(), e.getZ());
                if (owner != null) counts.merge(owner, 1, Integer::sum);
            } else if (e instanceof ItemFrame f) frames.add(f);
        }
        var p = client.player;
        for (var c : store.cities()) {
            int count = counts.getOrDefault(c, 0);
            boolean searchFresh = surveyed != null && surveyed.covers(c) && chunksLoaded(c.center, CityStore.RADIUS + 32);
            if (c.searchFresh != searchFresh) { c.searchFresh = searchFresh; revision++; }
            boolean covered = surveyed != null && surveyed.entityCoverage(c, p.getX(), p.getY(), p.getZ())
                && chunksLoaded(c.center, CityStore.RADIUS);
            boolean zero = quiet.computeIfAbsent("city:" + c.center.key(), k -> new SettledObservation()).advance(covered, count > 0);
            if (count > 0 || zero) {
                if (count != c.shulkers || !c.fresh) {
                    store.observe(c, count, true, System.currentTimeMillis()); revision++;
                }
            } else if (c.fresh) { c.fresh = false; revision++; }
        }
        for (var s : store.ships()) {
            boolean elytra = frames.stream().anyMatch(f -> nearFrame(s.frame, f) && f.getItem().is(Items.ELYTRA));
            boolean close = s.frame.distanceSquared(p.getX(), p.getZ()) <= 24 * 24
                && Math.abs(p.getY() - s.frame.y()) <= 24 && chunksLoaded(s.frame, 32);
            boolean absent = quiet.computeIfAbsent("ship:" + s.frame.key(), k -> new SettledObservation()).advance(close, elytra);
            if (elytra || absent) {
                var value = elytra ? Presence.PRESENT : Presence.ABSENT;
                if (s.elytra != value || !s.fresh) { store.observe(s, value); revision++; }
            } else if (s.fresh) { s.fresh = false; revision++; }
        }
    }
    private static boolean nearFrame(Pos p, ItemFrame f) {
        return ShipObservation.isDisplayFrame(p, f.getX(), f.getY(), f.getZ());
    }
    private boolean chunksLoaded(Pos p, int radius) {
        for (int x = (p.x() - radius) >> 4; x <= (p.x() + radius) >> 4; x++)
            for (int z = (p.z() - radius) >> 4; z <= (p.z() + radius) >> 4; z++)
                if (!level.hasChunk(x, z)) return false;
        return true;
    }
    private void reset() {
        if (xaero != null) xaero.clear();
        store = null; level = null; scanning = null; surveyed = null;
        context = ""; dimension = ""; quiet.clear(); tick = 0; surveyedTick = 0; revision++;
    }
    private boolean save() {
        if (store == null) return true;
        if (System.currentTimeMillis() < retry) return false;
        try { store.save(); retry = 0; return true; }
        catch (IOException ex) {
            LOG.error("Cannot save cities; retaining records in memory", ex);
            if (retry == 0) say("Cannot save cities; retrying. See latest.log.");
            retry = System.currentTimeMillis() + 5000; return false;
        }
    }
    private void status() {
        say((store == null ? 0 : store.cities().size()) + " cities and " + (store == null ? 0 : store.ships().size())
            + " ships recorded. Commands: here, scan, markers, mark <centerX> <centerZ>. Xaero: "
            + (xaero == null ? "unavailable" : markers ? "shown" : "hidden"));
    }
    private void here() {
        var player = Minecraft.getInstance().player;
        if (store == null || player == null) { say("No active End survey."); return; }
        var c = store.owner(player.getX(), player.getZ());
        if (c == null) {
            say("No recorded city within 192 blocks on each axis. Visit its entrance or use mark.");
            store.ships().stream().filter(s -> s.frame.distanceSquared(player.getX(), player.getZ()) <= 192 * 192)
                .min(Comparator.comparingDouble(s -> s.frame.distanceSquared(player.getX(), player.getZ())))
                .ifPresent(this::showShip);
            return;
        }
        say(store.label(c) + " at " + c.center.x() + ", " + c.center.z());
        for (var s : store.ships(c)) showShip(s);
        say("Counts are client observations. Unknown is not cleared. Visit the ship room for 5 seconds to check missing elytra.");
    }
    private void showShip(CityStore.Ship s) {
        say("Ship elytra room: " + s.frame.x() + ", " + s.frame.y() + ", " + s.frame.z()
            + "; elytra: " + s.elytra.name().toLowerCase(Locale.ROOT) + (s.fresh ? "" : " (needs a close visit)"));
    }
    private static void say(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[End City Scout] " + text));
    }
}
