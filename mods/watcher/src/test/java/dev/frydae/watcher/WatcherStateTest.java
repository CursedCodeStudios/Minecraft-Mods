package dev.frydae.watcher;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WatcherStateTest {
    @TempDir Path directory;

    @Test void reconnectIsEnabledByDefaultAndDisableSurvivesRestart() throws Exception {
        Path file = directory.resolve("watcher/state.properties");
        var state = new WatcherState(file);
        assertTrue(state.autoReconnect());
        state.setAutoReconnect(false);
        assertFalse(new WatcherState(file).autoReconnect());
        state.setAutoReconnect(true);
        assertTrue(new WatcherState(file).autoReconnect());
    }

    @Test void remembersPauseAcrossReconnectsWithoutLeakingToOtherServers() throws Exception {
        Path file = directory.resolve("watcher/state.properties");
        var state = new WatcherState(file);
        assertTrue(state.enabled("server:first"));
        assertFalse(state.paused("server:first"));
        state.setPaused("server:first", true);
        state.setEnabled("server:first", false);

        var reloaded = new WatcherState(file);
        assertTrue(reloaded.paused("server:first"));
        assertFalse(reloaded.enabled("server:first"));
        assertFalse(reloaded.paused("server:second"));
        assertTrue(reloaded.enabled("server:second"));
        reloaded.setEnabled("server:first", true);
        assertTrue(reloaded.paused("server:first"));
        reloaded.setPaused("server:first", false);
        assertFalse(new WatcherState(file).paused("server:first"));
    }

    @Test void localAcknowledgementSurvivesRestartAndDoesNotOverwriteLaterChatPause() throws Exception {
        Path file = directory.resolve("watcher/state.properties");
        var state = new WatcherState(file);
        state.applyLocalPermission("server:first", false, "resume-1");
        state.setPaused("server:first", true);
        var reloaded = new WatcherState(file);
        assertTrue(reloaded.paused("server:first"));
        assertEquals("resume-1", reloaded.localRevision("server:first"));
        assertEquals("", reloaded.localRevision("server:second"));
        reloaded.applyLocalPermission("server:first", false, "resume-2");
        assertFalse(new WatcherState(file).paused("server:first"));
        assertEquals("resume-2", new WatcherState(file).localRevision("server:first"));
    }
}
