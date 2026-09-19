package dev.slimescout;

import java.util.*;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

/** Loaded only when Xaero's Minimap is installed. Owns only temporary waypoints. */
final class XaeroBridge {
    private static final String SET = "Slime Scout";
    private WaypointSet attached;
    private final Map<String, Waypoint> owned = new HashMap<>();
    private MinimapWorld world;
    private int revision = -1;
    void clear() {
        if (attached != null) attached.removeAll(owned.values());
        owned.clear(); attached = null; world = null; revision = -1;
    }
    void sync(SightingStore store, String dimension, boolean visible, int version) {
        var session = XaeroMinimapSession.getCurrentSession();
        if (session == null) { clear(); return; }
        var manager = session.getMinimapProcessor().getSession().getWorldManager();
        var target = manager.getAutoWorld();
        if (target == null || target.getDimId() == null || !target.getDimId().identifier().toString().equals(dimension)) {
            clear(); return;
        }
        if (target == world && revision == version) return;
        if (target != world || !visible) clear();
        world = target; revision = version;
        if (!visible || store.entries().isEmpty()) return;
        attached = target.getWaypointSet(SET);
        if (attached == null) {
            target.addWaypointSet(SET);
            attached = target.getWaypointSet(SET);
            target.setCurrentWaypointSetId(SET);
        }
        for (var e : store.entries()) {
            String key = e.x + "," + e.z;
            String name = (e.underground ? "Potential slime chunk " : "Slime sighting ")
                + e.x + ", " + e.z + " [visits: " + e.visits + "]";
            var color = e.underground ? WaypointColor.GREEN : WaypointColor.YELLOW;
            var marker = owned.get(key);
            if (marker == null) {
                marker = new Waypoint(e.x * 16 + 8, e.minY, e.z * 16 + 8, name, "S?", color);
                marker.setTemporary(true);
                marker.setYIncluded(false);
                attached.add(marker);
                owned.put(key, marker);
            } else {
                marker.setName(name);
                marker.setY(e.minY);
                marker.setWaypointColor(color);
            }
        }
    }
}
