package dev.frydae.utilities.alts;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AltDashboardScreen extends Screen {
    private final Screen parent;
    private int page;
    private int ticks;
    public AltDashboardScreen(Screen parent) { super(Component.literal("Local Alt Affairs accounts")); this.parent = parent; }
    @Override protected void init() {
        int left = 12, content = Math.max(100, width - 24);
        line(left, 8, content, title.getString());
        String server = AltDashboard.server(minecraft);
        var accounts = AltDashboard.accounts();
        var permission = accounts.permission(server);
        line(left, 30, content, "This server: " + (server.isEmpty() ? "Not connected" : server));
        line(left, 50, content, "Latest local request: " + (permission == null ? "No local override"
            : permission.paused() ? "Paused" : "Allowed") + " (persists until changed)");
        int buttonWidth = Math.min(180, (content - 8) / 2);
        var pause = addRenderableWidget(Button.builder(Component.literal("Pause sleeping"), b -> AltDashboard.setPaused(true))
            .bounds(left, 74, buttonWidth, 20).build());
        var resume = addRenderableWidget(Button.builder(Component.literal("Allow sleeping"), b -> AltDashboard.setPaused(false))
            .bounds(left + buttonWidth + 8, 74, buttonWidth, 20).build());
        pause.active = resume.active = !server.isEmpty();
        int rows = Math.max(1, (height - 178) / 40);
        var peers = accounts.peers();
        int pages = Math.max(1, (peers.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        long now = System.currentTimeMillis();
        if (peers.isEmpty()) line(left, 104, content, "Waiting for local Alt Affairs accounts…");
        for (int i = page * rows, y = 104; i < Math.min(peers.size(), (page + 1) * rows); i++, y += 40) {
            var peer = peers.get(i);
            boolean online = peer.online(now);
            var peerPermission = peer.server().equals(server) ? permission : null;
            boolean pending = online && peerPermission != null && !peerPermission.revision().equals(peer.revision());
            line(left, y, content, peer.name() + " — " + (online ? peer.status() : "Offline (heartbeat expired)")
                + (pending ? " — control pending" : ""));
            line(left, y + 16, content, peer.server().isEmpty() ? "Not connected to a server yet" : peer.server());
        }
        if (!accounts.error().isEmpty()) line(left, height - 70, content, accounts.error());
        else line(left, height - 70, content, "Controls affect this server only. Alts still need a reachable bed.");
        var prev = addRenderableWidget(Button.builder(Component.literal("Previous"), b -> { page--; rebuildWidgets(); })
            .bounds(left, height - 44, 80, 20).build());
        prev.active = page > 0;
        line(left + 84, height - 44, 70, (page + 1) + " / " + pages);
        var next = addRenderableWidget(Button.builder(Component.literal("Next"), b -> { page++; rebuildWidgets(); })
            .bounds(left + 158, height - 44, 80, 20).build());
        next.active = page + 1 < pages;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(width - 92, height - 24, 80, 20).build());
    }
    private void line(int x, int y, int width, String value) {
        addRenderableOnly(new StringWidget(x, y, width, 16,
            Component.literal(font.plainSubstrByWidth(value, width)), font));
    }
    @Override public void tick() { if (++ticks % 20 == 0) rebuildWidgets(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
