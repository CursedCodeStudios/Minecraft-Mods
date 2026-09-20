package dev.monumentscout;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;

public final class MonumentScout implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("monumentscout");
    private static final int BLOCKS_PER_TICK = 4096;
    private static final int ZERO_ELDER_SETTLE_TICKS = 100;
    private final Map<String, Integer> quietTicks = new HashMap<>();
    private final Map<String, Long> nextSurvey = new HashMap<>();
    private ClientLevel level;
    private MonumentStore store;
    private MonumentSurvey survey;
    private XaeroBridge xaero;
    private String context = "", dimension = "";
    private long tick, saveRetry;
    private int revision;
    private boolean markers = true;

    @Override public void onInitializeClient() {
        if (FabricLoader.getInstance().isModLoaded("xaerominimap")) xaero = new XaeroBridge();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { saveRetry = 0; save(); });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (world != level || store == null) return;
            var pos = chunk.getPos();
            for (var entry : store.entries()) {
                if (entry.bounds().containsChunk(pos.x(), pos.z())) {
                    entry.fresh = false; revision++;
                    quietTicks.remove(entry.bounds().key());
                    if (survey != null && survey.bounds().equals(entry.bounds())) survey.invalidate();
                }
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            literal("monumentscout").executes(c -> { status(); return 1; })
                .then(literal("here").executes(c -> { here(); return 1; }))
                .then(literal("scan").executes(c -> {
                    nextSurvey.clear(); say("Nearby monuments queued for a new survey."); return 1;
                }))
                .then(literal("markers").executes(c -> {
                    markers = !markers; revision++; say("Markers " + (markers ? "shown" : "hidden")); return 1;
                }))
                .then(literal("mark").then(argument("centerX", IntegerArgumentType.integer(-29_999_900, 29_999_900))
                    .then(argument("centerZ", IntegerArgumentType.integer(-29_999_900, 29_999_900))
                        .executes(c -> {
                            if (store == null) { say("Join an Overworld first."); return 0; }
                            discover(new MonumentBounds(IntegerArgumentType.getInteger(c, "centerX"),
                                IntegerArgumentType.getInteger(c, "centerZ")), true);
                            return 1;
                        }))))
        ));
    }

    private void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            if (store != null || !context.isEmpty()) { if (!save()) return; reset(); }
            return;
        }
        String dim = client.level.dimension().identifier().toString();
        String world;
        if (client.getSingleplayerServer() != null)
            world = "local:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        else if (client.getCurrentServer() != null)
            world = "server:" + client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        else return;
        String next = world + "\n" + dim;
        if (!next.equals(context) || level != client.level) {
            if (!save()) return;
            reset(); context = next; dimension = dim; level = client.level;
            if (!dim.equals("minecraft:overworld")) return;
            try {
                store = new MonumentStore(FabricLoader.getInstance().getConfigDir().resolve("monument-scout"), world, dim);
            } catch (IOException ex) {
                LOG.error("Cannot open monument records", ex);
                say("Cannot open saved monuments; tracking disabled for this world. See latest.log.");
            }
        }
        if (store == null) return;
        tick++;
        var reader = new WorldReader(level);
        if (tick % 40 == 1) detect(client, reader);

        var elders = new ArrayList<net.minecraft.world.entity.Entity>();
        for (var entity : level.entitiesForRendering())
            if (entity.getType() == EntityTypes.ELDER_GUARDIAN && entity.isAlive()) elders.add(entity);

        for (var entry : store.entries()) {
            var bounds = entry.bounds();
            boolean nearby = bounds.distanceSquared(client.player.getX(), client.player.getZ()) <= 128 * 128;
            if (!nearby) {
                quietTicks.remove(bounds.key());
                if (entry.fresh) { entry.fresh = false; revision++; }
                continue;
            }
            int count = countElders(bounds, elders);
            boolean ready = reader.allChunksLoaded(bounds) && reader.elderChunksLoaded(bounds)
                && bounds.canSurveyElders(client.player.getX(), client.player.getY(), client.player.getZ());
            if (ready && count == 0) quietTicks.merge(bounds.key(), 1, (a, b) -> Math.min(a + b, ZERO_ELDER_SETTLE_TICKS));
            else quietTicks.remove(bounds.key());
            if ((!reader.allChunksLoaded(bounds) || (entry.elders == 0 && !ready)) && entry.fresh) { entry.fresh = false; revision++; }
            // Positive sightings immediately revoke an old "cleared" status.
            if (count > 0 && count != entry.elders) { store.observeElders(entry, count); revision++; }
        }

        if (survey == null) {
            var nextEntry = store.entries().stream()
                .filter(e -> nextSurvey.getOrDefault(e.bounds().key(), 0L) <= tick)
                .filter(e -> e.bounds().distanceSquared(client.player.getX(), client.player.getZ()) <= 128 * 128)
                .filter(e -> reader.allChunksLoaded(e.bounds()))
                .min(Comparator.comparingLong(e -> nextSurvey.getOrDefault(e.bounds().key(), 0L)));
            nextEntry.ifPresent(e -> survey = new MonumentSurvey(e.bounds()));
        }
        if (survey != null) {
            survey.advance(reader, BLOCKS_PER_TICK);
            if (survey.invalid()) { survey = null; }
            else if (survey.complete()) {
                var entry = store.get(survey.bounds());
                int visibleElders = countElders(survey.bounds(), elders);
                int count = visibleElders > 0 ? visibleElders
                    : quietTicks.getOrDefault(survey.bounds().key(), 0) >= ZERO_ELDER_SETTLE_TICKS ? 0 : -1;
                var old = entry.state();
                store.update(entry, survey.sponges(), count, System.currentTimeMillis());
                revision++;
                if (entry.state() != old) say(entry.label() + " at " + entry.centerX + ", " + entry.centerZ);
                nextSurvey.put(survey.bounds().key(), tick + 100);
                survey = null;
            }
        }
        if (tick % 100 == 0) save();
        if (xaero != null && tick % 10 == 0) {
            try { xaero.sync(store, dimension, markers, revision); }
            catch (RuntimeException | LinkageError ex) {
                LOG.error("Xaero integration failed", ex);
                try { xaero.clear(); } catch (RuntimeException | LinkageError ignored) { }
                xaero = null; say("Xaero integration unavailable; monument tracking continues. See latest.log.");
            }
        }
    }

    private void detect(Minecraft client, WorldReader reader) {
        var chunk = client.player.chunkPosition();
        for (int x = chunk.x() - 8; x <= chunk.x() + 8; x++) {
            for (int z = chunk.z() - 8; z <= chunk.z() + 8; z++) {
                if (Math.abs((long) x * 16) > 29_999_900 || Math.abs((long) z * 16) > 29_999_900) continue;
                var bounds = new MonumentBounds(x * 16, z * 16);
                if (store.get(bounds) == null && MonumentSignature.matches(bounds.centerX(), bounds.centerZ(), reader))
                    discover(bounds, false);
            }
        }
    }
    private void discover(MonumentBounds bounds, boolean manual) {
        if (store.discover(bounds, manual, System.currentTimeMillis())) {
            revision++; say("Monument recorded at " + bounds.centerX() + ", " + bounds.centerZ() + "; survey pending.");
        } else say("This monument is already recorded.");
    }
    private static int countElders(MonumentBounds bounds, List<net.minecraft.world.entity.Entity> elders) {
        return (int) elders.stream().filter(e -> bounds.containsElder(e.getX(), e.getY(), e.getZ())).count();
    }
    private void reset() {
        if (xaero != null) xaero.clear();
        store = null; level = null; survey = null; context = ""; dimension = "";
        quietTicks.clear(); nextSurvey.clear(); tick = 0; revision++;
    }
    private boolean save() {
        if (store == null) return true;
        if (System.currentTimeMillis() < saveRetry) return false;
        try { store.save(); saveRetry = 0; return true; }
        catch (IOException ex) {
            LOG.error("Cannot save monuments; retaining data in memory", ex);
            if (saveRetry == 0) say("Cannot save monuments; retrying. See latest.log.");
            saveRetry = System.currentTimeMillis() + 5000;
            return false;
        }
    }
    private void status() {
        say((store == null ? 0 : store.entries().size()) + " monuments recorded in this world. "
            + "Commands: here, scan, markers, mark <centerX> <centerZ>. Xaero: "
            + (xaero == null ? "unavailable" : markers ? "shown" : "hidden"));
    }
    private void here() {
        var player = Minecraft.getInstance().player;
        if (store == null || player == null) { say("No active Overworld survey."); return; }
        var nearest = store.entries().stream()
            .filter(e -> e.bounds().distanceSquared(player.getX(), player.getZ()) <= 128 * 128)
            .min(Comparator.comparingDouble(e -> e.bounds().distanceSquared(player.getX(), player.getZ())));
        if (nearest.isEmpty()) say("No recorded monument within 128 blocks.");
        else {
            var e = nearest.get();
            say(e.label() + " at " + e.centerX + ", " + e.centerZ
                + ". Stay near the monument center for at least 5 seconds to survey zero elders.");
        }
    }
    private static void say(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[Monument Scout] " + text));
    }

    private static final class WorldReader implements MonumentSurvey.Reader, MonumentSignature.Reader {
        private final ClientLevel level;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        WorldReader(ClientLevel level) { this.level = level; }
        @Override public boolean allChunksLoaded(MonumentBounds bounds) { return chunksLoaded(bounds, 0); }
        boolean elderChunksLoaded(MonumentBounds bounds) { return chunksLoaded(bounds, 16); }
        private boolean chunksLoaded(MonumentBounds b, int margin) {
            for (int x = (b.minX() - margin) >> 4; x <= (b.maxX() + margin) >> 4; x++)
                for (int z = (b.minZ() - margin) >> 4; z <= (b.maxZ() + margin) >> 4; z++)
                    if (!level.hasChunk(x, z)) return false;
            return true;
        }
        @Override public boolean isSponge(int x, int y, int z) {
            var state = level.getBlockState(cursor.set(x, y, z));
            return state.is(Blocks.SPONGE) || state.is(Blocks.WET_SPONGE);
        }
        @Override public MonumentSignature.Block get(int x, int y, int z) {
            if (!level.hasChunk(x >> 4, z >> 4)) return MonumentSignature.Block.UNLOADED;
            var state = level.getBlockState(cursor.set(x, y, z));
            if (state.is(Blocks.SEA_LANTERN)) return MonumentSignature.Block.LANTERN;
            if (state.is(Blocks.PRISMARINE_BRICKS)) return MonumentSignature.Block.BRICKS;
            if (state.is(Blocks.PRISMARINE)) return MonumentSignature.Block.PRISMARINE;
            return MonumentSignature.Block.OTHER;
        }
    }
}
