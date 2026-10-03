package dev.frydae.utilities.alts;

import dev.frydae.accounts.LocalAccounts;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class AltDashboard {
    private static LocalAccounts accounts;
    private static boolean open;
    private AltDashboard() {}
    public static void initialize() {
        accounts = new LocalAccounts(false);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> accounts.close());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            accounts.publish(client.getUser().getName(), server(client), "Controller", "");
            if (open) { open = false; client.gui.setScreen(new AltDashboardScreen(null)); }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            literal("alts").executes(c -> { open = true; return 1; })
                .then(literal("status").executes(c -> {
                    var client = Minecraft.getInstance();
                    long now = System.currentTimeMillis();
                    if (accounts.peers().isEmpty()) say("No local Alt Affairs accounts found yet.");
                    for (var peer : accounts.peers()) say(peer.name() + " — "
                        + (peer.online(now) ? peer.status() : "Offline (heartbeat expired)") + " — " + peer.server());
                    if (!accounts.error().isEmpty()) say(accounts.error());
                    return 1;
                }))
                .then(literal("sleep")
                    .then(literal("pause").executes(c -> { setPaused(true); return 1; }))
                    .then(literal("resume").executes(c -> { setPaused(false); return 1; })))));
    }
    public static LocalAccounts accounts() { return accounts; }
    public static String server(Minecraft client) {
        if (client.player == null || client.level == null) return "";
        if (client.getSingleplayerServer() != null)
            return "local:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        return client.getCurrentServer() == null ? "" : "server:" + client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
    }
    public static void setPaused(boolean paused) {
        String server = server(Minecraft.getInstance());
        if (server.isEmpty()) { say("Join a server or world first."); return; }
        accounts.setPaused(server, paused);
        say("Local " + (paused ? "pause" : "resume") + " requested for this server. Check the dashboard for acknowledgement.");
    }
    private static void say(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[Fry Utilities] " + text));
    }
}
