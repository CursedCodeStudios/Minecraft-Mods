package dev.frydae.slime;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WaypointOwnershipTest {
    @Test void identifiesOnlyActuallyRemovedOwnedObjects() {
        var owned = new WaypointOwnership<Object>();
        var a = new Object(); var b = new Object();
        owned.put("0,0", a); owned.put("-1,2", b);
        assertEquals(Set.of("0,0"), owned.deletedFrom(List.of(b, new Object())));
        assertEquals(Set.of(), owned.deletedFrom(List.of(b)));
    }
    @Test void hideAndWorldCleanupDoNotTurnIntoDeletion() {
        var owned = new WaypointOwnership<Object>();
        owned.put("0,0", new Object());
        owned.clear();
        assertTrue(owned.deletedFrom(List.of()).isEmpty());
    }
    @Test void markerStillPresentInAnotherSetIsNotDeleted() {
        var owned = new WaypointOwnership<Object>();
        var marker = new Object(); owned.put("0,0", marker);
        assertTrue(owned.deletedFrom(List.of(marker)).isEmpty());
        assertSame(marker, owned.get("0,0"));
    }
}
