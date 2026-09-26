package dev.frydae.endcity;

import java.util.*;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

/** Reuses one permanent waypoint per city center. */
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
    void sync(CityStore store, String dimension, boolean visible, int version) {
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
        var desired = new HashSet<String>();
        var desiredPositions = new HashSet<String>();
        for (var e : store.cities()) {
            String key = e.center.key();
            desired.add(key);
            desiredPositions.add(e.center.x() + "," + e.center.z());
            WaypointColor color = store.hasElytra(e) ? WaypointColor.BLUE : WaypointColor.YELLOW;
            String symbol = store.symbol(e);
            var marker = owned.get(key);
            if (marker == null) marker = permanent.find(e.center.x(), e.center.z(), XaeroBridge::isScoutName);
            if (marker == null) {
                marker = new Waypoint(e.center.x(), e.center.y(), e.center.z(), store.label(e), symbol, color);
                attached.add(marker);
            } else {
                marker.setName(store.label(e)); marker.setSymbol(symbol); marker.setWaypointColor(color);
            }
            marker.setTemporary(false); marker.setYIncluded(false);
            owned.put(key, marker); permanent.visible(marker, visible);
        }
        var iterator = owned.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!desired.contains(entry.getKey())) {
                permanent.remove(entry.getValue());
                iterator.remove();
            }
        }
        // A previously saved center may become a branch when the lower base is found.
        for (var marker : permanent.all())
            if (isScoutName(marker.getName()) && store.isRecordedEntrance(marker.getX(), marker.getZ())
                && !desiredPositions.contains(marker.getX() + "," + marker.getZ())) permanent.remove(marker);
        permanent.changed(); permanent.save(false);
    }
    static boolean isScoutName(String name) {
        // Adopt both old permanent names and the compact names without duplicating markers.
        return name.startsWith("End City [") || name.startsWith("End City (");
    }
}
