package dev.frydae.watcher;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReconnectDelayTest {
    @Test void delaysStartupAndRepeatedFailures() {
        var delay = new ReconnectDelay();
        assertFalse(delay.ready(0, true));
        assertFalse(delay.ready(9_999, true));
        assertTrue(delay.ready(10_000, true));
        assertFalse(delay.ready(10_001, true));
        assertTrue(delay.ready(20_000, true));
    }

    @Test void doesNotOverlapConnectionsAndWaitsAgainAfterDisconnect() {
        var delay = new ReconnectDelay();
        delay.ready(0, true);
        assertTrue(delay.ready(10_000, true));
        assertFalse(delay.ready(11_000, false));
        assertFalse(delay.ready(50_000, false));
        assertFalse(delay.ready(51_000, true));
        assertFalse(delay.ready(60_999, true));
        assertTrue(delay.ready(61_000, true));
    }
}
