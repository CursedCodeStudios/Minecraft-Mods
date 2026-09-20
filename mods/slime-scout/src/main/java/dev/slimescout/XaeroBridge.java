package dev.slimescout;

import java.util.*;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

/** Reuses permanent Xaero waypoints; user deletions suppress the recorded chunk. */
final class XaeroBridge {
    // Same set name in every scout; each bridge still owns only its own markers.
    private static final String SET = "Minecraft Scouts";
    private WaypointSet attached;
    private final WaypointOwnership<Waypoint> owned = new WaypointOwnership<>();
    private final PermanentWaypoints permanent = new PermanentWaypoints();
    private MinimapWorld world;
    private int revision = -1;
    void clear() {
        permanent.detach();
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
        if (changed) permanent.changed();
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
        permanent.save(false);
        if (target == world && revision == version) return;
        if (target != world) clear();
        world = target; revision = version;
        permanent.attach(target, session.getMinimapProcessor().getSession().getWorldManagerIO());
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
                if (removed == null) removed = permanent.find(e.x * 16 + 8, e.z * 16 + 8, XaeroBridge::isScoutName);
                if (removed != null) permanent.remove(removed);
                continue;
            }
            String name = (e.underground ? "Potential slime chunk " : "Slime sighting ")
                + e.x + ", " + e.z + " [visits: " + e.visits + "]";
            var color = e.underground ? xaero.hud.minimap.waypoint.WaypointColor.GREEN
                : xaero.hud.minimap.waypoint.WaypointColor.YELLOW;
            var marker = owned.get(key);
            if (marker == null) marker = permanent.find(e.x * 16 + 8, e.z * 16 + 8, XaeroBridge::isScoutName);
            if (marker == null) {
                marker = new Waypoint(e.x * 16 + 8, e.minY, e.z * 16 + 8, name, "S?", color);
                attached.add(marker);
            } else {
                marker.setName(name); marker.setY(e.minY); marker.setWaypointColor(color);
            }
            marker.setTemporary(false); marker.setYIncluded(false);
            owned.put(key, marker); permanent.visible(marker, visible);
        }
        permanent.changed(); permanent.save(false);
    }
    private static boolean isScoutName(String name) {
        return name.startsWith("Potential slime chunk ") || name.startsWith("Slime sighting ");
    }
}
