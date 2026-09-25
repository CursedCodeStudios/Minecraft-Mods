package dev.bedrockscout;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.Locale;

public final class BedrockScout implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("bedrockscout");
    private ClientLevel level;
    private BedrockStore store;
    private BedrockScanner scanner;
    private XaeroBridge xaero;
    private String context = "", dimension = "";
    private long ticks, retry;
    private int revision;
    private boolean markers = true;

    @Override public void onInitializeClient() {
        if (FabricLoader.getInstance().isModLoaded("xaerominimap")) xaero = new XaeroBridge();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (world == level && scanner != null) scanner.chunkLoaded(chunk.getPos().x(), chunk.getPos().z());
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { retry = 0; save(); if (xaero != null) xaero.clear(); });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            literal("bedrockscout").executes(c -> { status(); return 1; })
                .then(literal("here").executes(c -> { here(); return 1; }))
                .then(literal("scan").executes(c -> {
                    if (scanner == null || Minecraft.getInstance().player == null) { say("Join the Nether first."); return 0; }
                    var pos = Minecraft.getInstance().player.chunkPosition();
                    scanner.enqueueAround(pos.x(), pos.z(), 16, true);
                    say("Loaded roof chunks queued for a fresh scan."); return 1;
                }))
                .then(literal("markers").executes(c -> {
                    markers = !markers; revision++; say("Markers " + (markers ? "shown" : "hidden")); return 1;
                }))
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
            if (!dim.equals("minecraft:the_nether")) return;
            try {
                store = new BedrockStore(FabricLoader.getInstance().getConfigDir().resolve("bedrock-scout"), world, dim);
                scanner = new BedrockScanner(level);
            } catch (IOException ex) {
                LOG.error("Cannot open bedrock pattern records", ex);
                say("Cannot open saved patterns; tracking disabled for this world. See latest.log.");
            }
        }
        if (store == null || scanner == null) return;
        ticks++;
        if (ticks % 40 == 1) {
            var pos = client.player.chunkPosition();
            scanner.enqueueAround(pos.x(), pos.z(), 16, false);
        }
        for (var entry : scanner.advance(store)) {
            revision++;
            say("Exposed 3x3 roof bedrock centered at " + entry.x + ", Y=" + entry.y + ", " + entry.z + ".");
        }
        if (ticks % 100 == 0) save();
        if (xaero != null && ticks % 10 == 0) {
            try { xaero.sync(store, dimension, markers, revision); }
            catch (RuntimeException | LinkageError ex) {
                LOG.error("Xaero integration failed", ex);
                try { xaero.clear(); } catch (RuntimeException | LinkageError ignored) { }
                xaero = null; say("Xaero unavailable; pattern detection continues. See latest.log.");
            }
        }
    }
    private void reset() {
        if (xaero != null) xaero.clear();
        level = null; store = null; scanner = null; context = ""; dimension = ""; ticks = 0; revision++;
    }
    private boolean save() {
        if (store == null) return true;
        if (System.currentTimeMillis() < retry) return false;
        try { store.save(); retry = 0; return true; }
        catch (IOException ex) {
            LOG.error("Cannot save bedrock patterns; retaining them in memory", ex);
            if (retry == 0) say("Cannot save patterns; retrying. See latest.log.");
            retry = System.currentTimeMillis() + 5000; return false;
        }
    }
    private void status() {
        say((store == null ? 0 : store.entries().size()) + " exposed 3x3 roof patterns recorded; "
            + (scanner == null ? 0 : scanner.pending()) + " loaded chunks queued. Commands: here, scan, markers. Xaero: "
            + (xaero == null ? "unavailable" : markers ? "shown" : "hidden"));
    }
    private void here() {
        var player = Minecraft.getInstance().player;
        if (store == null || player == null) { say("No active Nether roof scan."); return; }
        var entry = store.nearest(player.getX(), player.getZ(), 512);
        if (entry == null) say("No recorded 3x3 roof pattern within 512 blocks.");
        else say(entry.label() + " centered at " + entry.x + ", " + entry.y + ", " + entry.z + ". Build directly below this center.");
    }
    private static void say(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[Bedrock Scout] " + text));
    }
}
