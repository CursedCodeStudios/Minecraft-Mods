package dev.endcityscout;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import org.slf4j.LoggerFactory;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.io.MinimapWorldManagerIO;

/** Xaero-owned permanent records; detaching never deletes waypoints. */
final class PermanentWaypoints {
    private MinimapWorld world;
    interface Saver { void save(MinimapWorld world) throws IOException; }
    private Saver saver;
    private final Map<Waypoint, Boolean> hidden = new IdentityHashMap<>();
    private boolean dirty;
    private long nextSave;

    void attach(MinimapWorld world, MinimapWorldManagerIO io) {
        attach(world, io::saveWorld);
    }
    void attach(MinimapWorld world, Saver saver) {
        this.world = world; this.saver = saver;
    }
    List<Waypoint> all() {
        var result = new ArrayList<Waypoint>();
        if (world != null) for (var set : world.getIterableWaypointSets()) set.addTo(result);
        return result;
    }
    Waypoint find(int x, int z, Predicate<String> names) {
        Waypoint found = null;
        for (var marker : all()) {
            if (marker.getX() != x || marker.getZ() != z || !names.test(marker.getName())) continue;
            if (found == null) found = marker;
            else remove(marker); // Collapse saved duplicates of this scout's marker only.
        }
        return found;
    }
    void remove(Waypoint marker) {
        hidden.remove(marker);
        for (var set : world.getIterableWaypointSets()) set.remove(marker);
        dirty = true;
    }
    void visible(Waypoint marker, boolean visible) {
        if (!visible) {
            if (!hidden.containsKey(marker)) hidden.put(marker, marker.getDisabled());
            marker.setDisabled(true);
        } else if (hidden.containsKey(marker)) marker.setDisabled(hidden.remove(marker));
    }
    void changed() { dirty = true; }
    void save(boolean force) {
        if (!dirty || world == null || (!force && System.currentTimeMillis() < nextSave)) return;
        // Our session-only hide command must not become a persisted disabled flag.
        hidden.forEach(Waypoint::setDisabled);
        try {
            saver.save(world); dirty = false;
        } catch (IOException ex) {
            LoggerFactory.getLogger("dev.endcityscout").error("Cannot save permanent Xaero waypoints; scout records retained", ex);
        } finally {
            hidden.keySet().forEach(marker -> marker.setDisabled(true));
            nextSave = System.currentTimeMillis() + 5000;
        }
    }
    void detach() {
        hidden.forEach(Waypoint::setDisabled);
        if (!hidden.isEmpty()) dirty = true;
        hidden.clear();
        save(true);
        world = null; saver = null; dirty = false; nextSave = 0;
    }
}
