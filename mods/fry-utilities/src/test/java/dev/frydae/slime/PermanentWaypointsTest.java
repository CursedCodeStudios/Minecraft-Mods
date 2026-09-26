package dev.frydae.slime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.world.MinimapWorld;

class PermanentWaypointsTest {
    private MinimapWorld world() throws Exception {
        // No live client/container is needed for the waypoint-set and serializer APIs.
        var constructor = MinimapWorld.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var world = (MinimapWorld) constructor.newInstance(null, "test", null);
        world.addWaypointSet("Minecraft Scouts");
        world.setCurrentWaypointSetId("Minecraft Scouts");
        return world;
    }
    private Waypoint marker(String name) {
        var w = new Waypoint(-24, 80, 40, name, "S", WaypointColor.GREEN);
        w.setTemporary(false); w.setYIncluded(false); return w;
    }
    @Test void savedMarkersSurviveDetachAndAreReusedAcrossSets() throws Exception {
        var world = world(); var w = marker("Scout example");
        world.addWaypointSet("Moved"); world.getWaypointSet("Moved").add(w);
        var saves = new AtomicInteger();
        var first = new PermanentWaypoints(); first.attach(world, ignored -> saves.incrementAndGet());
        first.changed(); first.detach();
        assertEquals(1, saves.get()); assertEquals(1, world.getWaypointSet("Moved").size());
        var next = new PermanentWaypoints(); next.attach(world, ignored -> { });
        assertSame(w, next.find(-24, 40, n -> n.startsWith("Scout ")));
        assertFalse(w.isTemporary());
    }
    @Test void duplicatesAreRemovedWithoutTouchingOtherScoutsOrUserMarkers() throws Exception {
        var world = world(); var set = world.getCurrentWaypointSet();
        var first = marker("Scout first"); var duplicate = marker("Scout old"); var user = marker("My home");
        set.add(first); set.add(duplicate); set.add(user);
        var helper = new PermanentWaypoints(); helper.attach(world, ignored -> { });
        assertSame(first, helper.find(-24, 40, n -> n.startsWith("Scout ")));
        assertEquals(2, set.size()); assertSame(user, set.get(1));
    }
    @Test void hidingIsNotDeletionAndSaveReceivesPermanentVisibleMarkers() throws Exception {
        var world = world(); var w = marker("Scout example"); world.getCurrentWaypointSet().add(w);
        var saves = new AtomicInteger();
        var helper = new PermanentWaypoints();
        helper.attach(world, target -> {
            assertFalse(w.isDisabled());
            assertFalse(w.isTemporary());
            saves.incrementAndGet();
        });
        helper.visible(w, false); assertTrue(w.isDisabled());
        helper.changed(); helper.save(true);
        assertTrue(w.isDisabled()); assertEquals(1, world.getCurrentWaypointSet().size());
        assertEquals(1, saves.get());
        helper.detach(); assertFalse(w.isDisabled());
        assertEquals(1, world.getCurrentWaypointSet().size());
    }
    @Test void failedSavesAreRetriedWithoutLosingMarkers() throws Exception {
        var world = world(); world.getCurrentWaypointSet().add(marker("Scout example"));
        var tries = new AtomicInteger(); var helper = new PermanentWaypoints();
        helper.attach(world, ignored -> { if (tries.incrementAndGet() == 1) throw new IOException("test failure"); });
        helper.changed(); helper.save(true); helper.save(true);
        assertEquals(2, tries.get()); assertEquals(1, world.getCurrentWaypointSet().size());
    }
}
