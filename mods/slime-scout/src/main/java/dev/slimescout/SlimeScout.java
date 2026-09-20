package dev.slimescout;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.io.IOException;

public final class SlimeScout implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("slimescout");
    private final ObservationTracker seen = new ObservationTracker();
    private final SplitFilter splits = new SplitFilter();
    private SightingStore store;
    private ClientLevel activeLevel;
    private String context = "", dimension = "";
    private int ticks, revision;
    private boolean enabled = true, markers = true;
    private XaeroBridge xaero;
    private long retrySaveAt;

    @Override public void onInitializeClient() {
        if (FabricLoader.getInstance().isModLoaded("xaerominimap")) xaero = new XaeroBridge();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (level == activeLevel && entity.getType() == EntityTypes.SLIME && entity instanceof Slime slime
                && (slime.getHealth() <= 0 || slime.getRemovalReason() == Entity.RemovalReason.KILLED)) {
                splits.death(sample(slime), ticks);
                if (store != null) store.forgetDeadSplitChild(slime.getUUID());
            }
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            if (store != null && level == Minecraft.getInstance().level
                    && dimension.equals(level.dimension().identifier().toString())) {
                var pos = chunk.getPos();
                store.unload(pos.x(), pos.z());
                seen.unload(pos.pack());
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { retrySaveAt = 0; save(); });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            literal("slimescout").executes(c -> { status(); return 1; })
                .then(literal("toggle").executes(c -> { enabled = !enabled; say("Tracking " + (enabled ? "enabled" : "paused")); return 1; }))
                .then(literal("markers").executes(c -> { markers = !markers; revision++; say("Xaero markers " + (markers ? "shown" : "hidden")); return 1; }))
                .then(literal("dismiss").executes(c -> { dismissHere(false); return 1; }))
                .then(literal("restore").executes(c -> { dismissHere(true); return 1; }))
                .then(literal("here").executes(c -> { here(); return 1; }))
                .then(literal("save").executes(c -> { retrySaveAt = 0; if (save()) say("Sightings saved."); return 1; }))
        ));
    }
    private void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            if (!context.isEmpty()) { if (!save()) return; reset(); }
            return;
        }
        String dim = client.level.dimension().identifier().toString();
        String world;
        if (client.getSingleplayerServer() != null)
            world = "local:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        else if (client.getCurrentServer() != null) world = "server:" + client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        else return; // Never merge unidentified connections into a shared bucket.
        String next = world + "\n" + dim;
        if (!next.equals(context) || client.level != activeLevel) {
            if (!save()) return;
            reset(); context = next; dimension = dim; activeLevel = client.level;
            try { store = new SightingStore(FabricLoader.getInstance().getConfigDir().resolve("slime-scout"), world, dim); }
            catch (IOException ex) { LOG.error("Sightings could not be opened", ex); say("Cannot open sightings; tracking disabled for this world. See latest.log."); }
        }
        if (store == null) return;
        ticks++;
        // Collect all dying parents before considering any children, independent of entity iteration order.
        var slimes = new ArrayList<Slime>();
        for (var entity : client.level.entitiesForRendering()) {
            if (entity.getType() != EntityTypes.SLIME || !(entity instanceof Slime slime)) continue;
            if (slime.getHealth() <= 0) {
                splits.death(sample(slime), ticks);
                store.forgetDeadSplitChild(slime.getUUID());
            }
            else if (slime.isAlive()) slimes.add(slime);
        }
        Set<UUID> loaded = new HashSet<>();
        for (Slime slime : slimes) {
            UUID id = slime.getUUID(); loaded.add(id);
            if (store.isSplitChild(id)) continue;
            var verdict = splits.observe(sample(slime), ticks);
            if (verdict == SplitFilter.Verdict.SPLIT) { store.ignoreSplitChild(id); continue; }
            if (verdict == SplitFilter.Verdict.WAIT || !enabled) continue;
            var pos = slime.chunkPosition();
            if (!client.level.hasChunk(pos.x(), pos.z())) continue;
            var observation = seen.observe(id, pos.pack());
            if (observation == ObservationTracker.Kind.NONE) continue;
            if (observation == ObservationTracker.Kind.FIRST) {
                var first = splits.first(id);
                record(first.chunkX(), first.chunkZ(), first.blockY(), dim.equals("minecraft:overworld") && first.y() < 40);
                if (first.chunkX() == pos.x() && first.chunkZ() == pos.z()) continue;
            }
            record(pos.x(), pos.z(), slime.blockPosition().getY(), false);
        }
        seen.retain(loaded);
        splits.retain(loaded, ticks);
        // Check deletions every tick, even if no sightings changed or markers are about to be hidden.
        if (xaero != null) {
            try {
                if (xaero.captureDeletions(store)) { revision++; save(); }
                if (ticks % 10 == 0) xaero.sync(store, dimension, markers, revision);
            } catch (RuntimeException | LinkageError ex) {
                LOG.error("Xaero integration unavailable", ex);
                try { xaero.clear(); } catch (RuntimeException | LinkageError ignored) { }
                xaero = null;
                say("Xaero integration failed; sightings still record. See latest.log.");
            }
        }
        if (ticks % 100 == 0) save();
    }
    private static SplitFilter.Sample sample(Slime slime) {
        return new SplitFilter.Sample(slime.getUUID(), slime.getSize(), slime.getX(), slime.getY(), slime.getZ(), slime.getBbWidth());
    }
    private void record(int x, int z, int y, boolean candidate) {
        if (store.isDismissed(x + "," + z)) return;
        if (store.record(x, z, y, candidate, System.currentTimeMillis()))
            say((candidate ? "Potential slime chunk" : "Slime sighting") + " at chunk " + x + ", " + z);
        revision++;
    }
    private void dismissHere(boolean restore) {
        var player = Minecraft.getInstance().player;
        if (player == null || store == null) return;
        var pos = player.chunkPosition();
        boolean changed = restore ? store.restore(pos.x(), pos.z()) : store.dismiss(pos.x() + "," + pos.z());
        if (changed) { revision++; save(); }
        say(changed ? (restore ? "Restored" : "Dismissed") + " chunk " + pos.x() + ", " + pos.z()
            : "No matching " + (restore ? "dismissed " : "") + "record to change here.");
    }
    private void reset() {
        if (xaero != null) xaero.clear();
        store = null; activeLevel = null; seen.clear(); splits.clear(); context = ""; dimension = ""; revision++;
    }
    private boolean save() {
        if (store == null) return true;
        if (System.currentTimeMillis() < retrySaveAt) return false;
        try { store.save(); retrySaveAt = 0; return true; }
        catch (IOException ex) {
            LOG.error("Cannot save sightings; retaining them in memory", ex);
            if (retrySaveAt == 0) say("Cannot save sightings. Keeping them in memory and retrying; see latest.log.");
            retrySaveAt = System.currentTimeMillis() + 5000;
            return false;
        }
    }
    private void status() {
        long strong = store == null ? 0 : store.entries().stream().filter(e -> e.underground && !e.dismissed).count();
        say("Tracking " + (enabled ? "on" : "paused") + "; " + (store == null ? 0 : store.entries().stream().filter(e -> !e.dismissed).count())
            + " chunks, " + strong + " below-Y=40 candidates in this dimension. Xaero: " + (xaero == null ? "unavailable" : markers ? "on" : "hidden")
            + ". Commands: here, toggle, markers, dismiss, restore, save.");
    }
    private void here() {
        var player = Minecraft.getInstance().player;
        if (player == null || store == null) return;
        var pos = player.chunkPosition(); var e = store.get(pos.x(), pos.z());
        say(e == null ? "No slime sightings in chunk " + pos.x() + ", " + pos.z()
            : "Chunk " + e.x + ", " + e.z + ": " + e.visits + " slime-positive chunk loads; lowest Y=" + e.minY
                + (e.dismissed ? "; dismissed" : "") + "; " + (e.underground ? "potential slime chunk" : "unconfirmed sighting") + ".");
    }
    private static void say(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[Slime Scout] " + message));
    }
}
