package dev.monumentscout;

import java.util.*;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

/** Owns one temporary waypoint per monument, never one per elder or sponge. */
final class XaeroBridge {
    private static final String SET = "Monument Scout";
    private WaypointSet attached;
    private final Map<String, Waypoint> owned = new HashMap<>();
    private MinimapWorld world;
    private int revision = -1;
    void clear() {
        if (attached != null) attached.removeAll(owned.values());
        owned.clear(); attached = null; world = null; revision = -1;
    }
    void sync(MonumentStore store, String dimension, boolean visible, int version) {
        var session = XaeroMinimapSession.getCurrentSession();
        if (session == null) { clear(); return; }
        var target = session.getMinimapProcessor().getSession().getWorldManager().getAutoWorld();
        if (target == null || target.getDimId() == null || !target.getDimId().identifier().toString().equals(dimension)) {
            clear(); return;
        }
        if (target == world && revision == version) return;
        if (target != world || !visible) clear();
        world = target; revision = version;
        if (!visible || store.entries().isEmpty()) return;
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
            if (marker == null) {
                marker = new Waypoint(e.centerX, 61, e.centerZ, e.label(), symbol, color);
                marker.setTemporary(true); marker.setYIncluded(false);
                attached.add(marker); owned.put(key, marker);
            } else {
                marker.setName(e.label()); marker.setSymbol(symbol); marker.setWaypointColor(color);
            }
        }
    }
}
