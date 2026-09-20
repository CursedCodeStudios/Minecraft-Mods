package dev.slimescout;

import java.util.*;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

/** Owns temporary waypoints; user deletions become persistent per-chunk suppressions. */
final class XaeroBridge {
    private static final String SET = "Slime Scout";
    private WaypointSet attached;
    private final WaypointOwnership<Waypoint> owned = new WaypointOwnership<>();
    private MinimapWorld world;
    private int revision = -1;
    void clear() {
        // Our own hiding/world-change cleanup is not a user deletion.
        if (world != null) for (var set : world.getIterableWaypointSets()) set.removeAll(owned.values());
        owned.clear(); attached = null; world = null; revision = -1;
    }
    boolean captureDeletions(SightingStore store) {
        if (world == null || attached == null || owned.isEmpty()) return false;
        var session = XaeroMinimapSession.getCurrentSession();
        if (session == null || session.getMinimapProcessor().getSession().getWorldManager().getAutoWorld() != world)
            return false;
        var present = new ArrayList<Waypoint>();
        // A marker moved to a different set is not deleted.
        for (var set : world.getIterableWaypointSets()) set.addTo(present);
        boolean changed = false;
        for (String key : owned.deletedFrom(present)) changed |= store.dismiss(key);
        return changed;
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
        if (!visible || store.entries().stream().allMatch(e -> e.dismissed)) { clear(); return; }
        attached = target.getWaypointSet(SET);
        if (attached == null) {
            target.addWaypointSet(SET);
            attached = target.getWaypointSet(SET);
            target.setCurrentWaypointSetId(SET);
        }
        for (var e : store.entries()) {
            String key = e.x + "," + e.z;
            if (e.dismissed) {
                var removed = owned.remove(key);
                if (removed != null) for (var set : world.getIterableWaypointSets()) set.remove(removed);
                continue;
            }
            String name = (e.underground ? "Potential slime chunk " : "Slime sighting ")
                + e.x + ", " + e.z + " [visits: " + e.visits + "]";
            var color = e.underground ? xaero.hud.minimap.waypoint.WaypointColor.GREEN
                : xaero.hud.minimap.waypoint.WaypointColor.YELLOW;
            var marker = owned.get(key);
            if (marker == null) {
                marker = new Waypoint(e.x * 16 + 8, e.minY, e.z * 16 + 8, name, "S?", color);
                marker.setTemporary(true); marker.setYIncluded(false);
                attached.add(marker); owned.put(key, marker);
            } else {
                marker.setName(name); marker.setY(e.minY); marker.setWaypointColor(color);
            }
        }
    }
}
