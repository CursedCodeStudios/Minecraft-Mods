package dev.frydae.monument;

import java.util.*;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

/** Reuses one permanent waypoint per monument, never one per elder or sponge. */
final class XaeroBridge {
    // Same set name in every scout; each bridge still owns only its own markers.
    private static final String SET = "Minecraft Scouts";
    private WaypointSet attached;
    private final Map<String, Waypoint> owned = new HashMap<>();
    private final PermanentWaypoints permanent = new PermanentWaypoints();
    private MinimapWorld world;
    private int revision = -1;
    void clear() {
        permanent.detach();
        owned.clear(); attached = null; world = null; revision = -1;
    }
    void sync(MonumentStore store, String dimension, boolean visible, int version) {
        var session = XaeroMinimapSession.getCurrentSession();
        if (session == null) { clear(); return; }
        var target = session.getMinimapProcessor().getSession().getWorldManager().getAutoWorld();
        if (target == null || target.getDimId() == null || !target.getDimId().identifier().toString().equals(dimension)) {
            clear(); return;
        }
        permanent.save(false);
        if (target == world && revision == version) return;
        if (target != world) clear();
        world = target; revision = version;
        permanent.attach(target, session.getMinimapProcessor().getSession().getWorldManagerIO());
        attached = target.getWaypointSet(SET);
        if (attached == null) {
            target.addWaypointSet(SET); attached = target.getWaypointSet(SET);
            target.setCurrentWaypointSetId(SET);
        }
        for (var e : store.entries()) {
            String key = e.bounds().key();
            WaypointColor color = switch (e.state()) {
                case DISCOVERED -> WaypointColor.GRAY;
                case SPONGES_REMAIN -> WaypointColor.AQUA;
                case NO_SPONGES -> WaypointColor.YELLOW;
                case CLEARED -> WaypointColor.GREEN;
            };
            String symbol = switch (e.state()) {
                case DISCOVERED -> "M?";
                case SPONGES_REMAIN -> "M";
                case NO_SPONGES -> "M-";
                case CLEARED -> "OK";
            };
            var marker = owned.get(key);
            if (marker == null) marker = permanent.find(e.centerX, e.centerZ, name -> name.startsWith("Monument: "));
            if (marker == null) {
                marker = new Waypoint(e.centerX, 61, e.centerZ, e.label(), symbol, color);
                attached.add(marker);
            } else {
                marker.setName(e.label()); marker.setSymbol(symbol); marker.setWaypointColor(color);
            }
            marker.setTemporary(false); marker.setYIncluded(false);
            owned.put(key, marker); permanent.visible(marker, visible);
        }
        permanent.changed(); permanent.save(false);
    }
}
