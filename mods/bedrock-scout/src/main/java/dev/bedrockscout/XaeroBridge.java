package dev.bedrockscout;

import java.util.*;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

final class XaeroBridge {
    private static final String SET = "Minecraft Scouts";
    private WaypointSet attached;
    private final Map<String, Waypoint> owned = new HashMap<>();
    private final PermanentWaypoints permanent = new PermanentWaypoints();
    private MinimapWorld world;
    private int revision = -1;
    void clear() {
        permanent.detach(); owned.clear(); attached = null; world = null; revision = -1;
    }
    void sync(BedrockStore store, String dimension, boolean visible, int version) {
        var session = XaeroMinimapSession.getCurrentSession();
        if (session == null) { clear(); return; }
        var minimap = session.getMinimapProcessor().getSession();
        var target = minimap.getWorldManager().getAutoWorld();
        if (target == null || target.getDimId() == null || !target.getDimId().identifier().toString().equals(dimension)) {
            clear(); return;
        }
        permanent.save(false);
        if (target == world && revision == version) return;
        if (target != world) clear();
        world = target; revision = version;
        permanent.attach(target, minimap.getWorldManagerIO());
        attached = target.getWaypointSet(SET);
        if (attached == null) {
            target.addWaypointSet(SET); attached = target.getWaypointSet(SET); target.setCurrentWaypointSetId(SET);
        }
        for (var e : store.entries()) {
            String key = e.key();
            var marker = owned.get(key);
            if (marker == null) marker = permanent.find(e.x, e.z, name -> name.startsWith("Nether Roof 3x3"));
            if (marker == null) {
                marker = new Waypoint(e.x, e.y - 1, e.z, e.label(), "B3", WaypointColor.PURPLE);
                attached.add(marker);
            } else {
                marker.setName(e.label()); marker.setSymbol("B3"); marker.setY(e.y - 1); marker.setWaypointColor(WaypointColor.PURPLE);
            }
            marker.setTemporary(false); marker.setYIncluded(true);
            owned.put(key, marker); permanent.visible(marker, visible);
        }
        permanent.changed(); permanent.save(false);
    }
}
