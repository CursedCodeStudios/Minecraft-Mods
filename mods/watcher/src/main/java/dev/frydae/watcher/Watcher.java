package dev.frydae.watcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import dev.frydae.accounts.LocalAccounts;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class Watcher implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("watcher");
    private WatcherState state;
    private String context = "";
    private boolean paused;
    private boolean enabled;
    private long tick;
    private long nextScan;
    private long nextWake;
    private final Map<BlockPos, Long> retries = new HashMap<>();
    private final ReconnectDelay reconnectDelay = new ReconnectDelay();
    private LocalAccounts accounts;
    private String lastServer = "";
    private String sleepStatus = "Waiting for night";

    @Override public void onInitializeClient() {
        try { state = new WatcherState(FabricLoader.getInstance().getConfigDir().resolve("watcher/state.properties")); }
        catch (IOException ex) { LOG.error("Cannot load Watcher state; automatic sleeping disabled", ex); }
        accounts = new LocalAccounts(true);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> accounts.close());
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, received) -> {
            var client = Minecraft.getInstance();
            if (client.player == null) return;
            receive(signed == null ? message.getString() : signed.signedContent(), signed == null);
        });
        // Servers with chat plugins often send player chat as system messages.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) receive(message.getString(), true);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            literal("watcher").executes(c -> { status(); return 1; })
                .then(literal("on").executes(c -> { setEnabled(true); return 1; }))
                .then(literal("off").executes(c -> { setEnabled(false); return 1; }))
                .then(literal("reconnect")
                    .then(literal("on").executes(c -> { setReconnect(true); return 1; }))
                    .then(literal("off").executes(c -> { setReconnect(false); return 1; })))));
    }

    private void refreshContext(Minecraft client) {
        String current = "";
        if (client.player != null && client.level != null) {
            if (client.getSingleplayerServer() != null)
                current = "local:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            else if (client.getCurrentServer() != null)
                current = "server:" + client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        }
        if (current.equals(context)) return;
        context = current;
        enabled = state != null && !context.isEmpty() && state.enabled(context);
        paused = state == null || (!context.isEmpty() && state.paused(context));
        retries.clear(); tick = 0; nextScan = 0; nextWake = 0;
    }

    private void receive(String text, boolean decorated) {
        var client = Minecraft.getInstance();
        refreshContext(client);
        if (context.isEmpty() || state == null) return;
        var request = SleepChat.parse(text, decorated);
        if (request == SleepChat.Request.NONE) return;
        boolean pause = request == SleepChat.Request.PAUSE;
        if (pause != paused) {
            paused = pause;
            try { state.setPaused(context, paused); }
            catch (IOException ex) { reportSaveFailure(ex); }
        }
        nextScan = 0;
        if (paused) wake(client);
        if (client.getConnection() != null)
            client.getConnection().sendChat(paused ? SleepChat.PAUSE_REPLY : SleepChat.RESUME_REPLY);
        if (!paused) {
            // Permission is a fresh attempt, even if this bed was tried before the pause.
            retries.clear();
            trySleep(client);
        }
    }

    private void tick(Minecraft client) {
        reconnect(client);
        refreshContext(client);
        tick++;
        if (!context.isEmpty() && state != null) {
            lastServer = context;
            var permission = accounts.permission(context);
            if (permission != null && !permission.revision().equals(state.localRevision(context))) {
                paused = permission.paused();
                retries.clear(); nextScan = 0;
                try { state.applyLocalPermission(context, paused, permission.revision()); }
                catch (IOException ex) { reportSaveFailure(ex); }
            }
        }
        if (paused) wake(client);
        else if (accounts.ready(context)) trySleep(client);
        else sleepStatus = "Waiting for local sleep permission";
        String status = context.isEmpty() ? (client.gui.screen() instanceof ConnectScreen ? "Connecting"
            : state != null && state.autoReconnect() ? "Disconnected / waiting to reconnect" : "Disconnected")
            : state == null ? "Settings error" : paused ? "Sleep paused" : !enabled ? "Auto sleep off"
            : client.player.isSleeping() ? "Sleeping" : sleepStatus;
        accounts.publish(client.getUser().getName(), context.isEmpty() ? lastServer : context,
            status, state == null || context.isEmpty() ? "" : state.localRevision(context));
    }

    private void trySleep(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) return;
        if (paused || !enabled) return;
        if (!client.level.dimension().equals(Level.OVERWORLD) || !client.player.isAlive()
            || client.player.isSleeping() || client.player.isSpectator() || client.player.isPassenger()
            || client.player.isShiftKeyDown()) { sleepStatus = "Waiting / unable to sleep"; return; }
        // The same environment rule is used by the server's startSleepInBed.
        var rule = client.level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, client.player.blockPosition());
        if (rule.explodes() || !rule.canSleep(client.level)) { sleepStatus = "Waiting for night"; retries.clear(); return; }
        if (tick < nextScan) return;
        nextScan = tick + 10;
        retries.entrySet().removeIf(entry -> entry.getValue() <= tick);
        var hit = nearestBed(client);
        if (hit == null) { sleepStatus = "No reachable bed / retrying"; return; }
        sleepStatus = "Trying bed";
        var block = client.level.getBlockState(hit.getBlockPos());
        var head = block.getValue(BedBlock.PART) == BedPart.HEAD ? hit.getBlockPos()
            : hit.getBlockPos().relative(block.getValue(BedBlock.FACING));
        retries.put(head.immutable(), tick + 100);
        retries.put(head.relative(block.getValue(BedBlock.FACING).getOpposite()), tick + 100);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
    }

    private void reconnect(Minecraft client) {
        boolean idle = state != null && state.autoReconnect() && client.level == null
            && client.getConnection() == null && !client.hasSingleplayerServer()
            && client.gui.overlay() == null && client.allowsMultiplayer()
            && (client.gui.screen() instanceof TitleScreen || client.gui.screen() instanceof DisconnectedScreen
                || client.gui.screen() instanceof JoinMultiplayerScreen);
        if (!reconnectDelay.ready(Util.getMillis(), idle)) return;
        try {
            var servers = new ServerList(client);
            servers.load();
            if (servers.size() == 0) return;
            var first = servers.get(0);
            if (!ServerAddress.isValidAddress(first.ip)) return;
            ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client,
                ServerAddress.parseString(first.ip), first, false, null);
        } catch (RuntimeException ex) {
            LOG.error("Automatic connection failed; will retry", ex);
        }
    }

    private void setReconnect(boolean value) {
        if (state == null) { say("Settings could not load. See latest.log."); return; }
        try { state.setAutoReconnect(value); }
        catch (IOException ex) { reportSaveFailure(ex); }
        status();
    }

    private BlockHitResult nearestBed(Minecraft client) {
        var player = client.player;
        var level = client.level;
        Vec3 eyes = player.getEyePosition();
        int radius = (int)Math.ceil(Math.min(player.blockInteractionRange(), 6));
        var origin = player.blockPosition();
        BlockHitResult closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (var pos : BlockPos.betweenClosed(origin.offset(-radius, -2, -radius), origin.offset(radius, 2, radius))) {
            if (!level.hasChunkAt(pos) || retries.getOrDefault(pos, 0L) > tick) continue;
            var block = level.getBlockState(pos);
            if (!(block.getBlock() instanceof BedBlock) || block.getValue(BedBlock.OCCUPIED)) continue;
            var head = block.getValue(BedBlock.PART) == BedPart.HEAD ? pos : pos.relative(block.getValue(BedBlock.FACING));
            var foot = head.relative(block.getValue(BedBlock.FACING).getOpposite());
            if (!level.hasChunkAt(head) || !level.hasChunkAt(foot)
                || !level.getBlockState(head).is(block.getBlock()) || !level.getBlockState(foot).is(block.getBlock())
                || level.getBlockState(head).getValue(BedBlock.OCCUPIED)) continue;
            if (!sleepReach(player.position(), head) && !sleepReach(player.position(), foot)) continue;
            var rule = level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, head);
            if (rule.explodes() || !rule.canSleep(level)) continue;
            Vec3 target = new Vec3(pos.getX() + .5, pos.getY() + .28, pos.getZ() + .5);
            if (eyes.distanceToSqr(target) > player.blockInteractionRange() * player.blockInteractionRange()) continue;
            var hit = level.clip(new ClipContext(eyes, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) continue;
            double distance = player.position().distanceToSqr(target);
            if (distance < closestDistance) { closestDistance = distance; closest = hit; }
        }
        return closest;
    }

    private static boolean sleepReach(Vec3 player, BlockPos bed) {
        return Math.abs(player.x() - bed.getX() - .5) <= 3
            && Math.abs(player.y() - bed.getY()) <= 2
            && Math.abs(player.z() - bed.getZ() - .5) <= 3;
    }

    private void wake(Minecraft client) {
        if (client.player != null && client.player.isSleeping() && client.getConnection() != null && tick >= nextWake) {
            client.getConnection().send(new ServerboundPlayerCommandPacket(client.player,
                ServerboundPlayerCommandPacket.Action.STOP_SLEEPING));
            nextWake = tick + 20;
        }
    }

    private void setEnabled(boolean value) {
        var client = Minecraft.getInstance();
        refreshContext(client);
        if (context.isEmpty() || state == null) { say("Join a world first; if state could not load, see latest.log."); return; }
        enabled = value; nextScan = 0;
        try { state.setEnabled(context, value); }
        catch (IOException ex) { reportSaveFailure(ex); }
        status();
    }

    private void status() {
        refreshContext(Minecraft.getInstance());
        say("Automatic sleeping " + (enabled ? "on" : "off") + "; sleep pause " + (paused ? "active" : "inactive")
            + "; automatic reconnect " + (state != null && state.autoReconnect() ? "on" : "off")
            + ". Use /watcher on|off or /watcher reconnect on|off.");
        if (!accounts.error().isEmpty()) say(accounts.error());
    }

    private void reportSaveFailure(IOException ex) {
        LOG.error("Cannot save Watcher state", ex);
        say("Settings changed for this session but could not be saved. See latest.log.");
    }

    private static void say(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[Alt Affairs] " + text));
    }
}
