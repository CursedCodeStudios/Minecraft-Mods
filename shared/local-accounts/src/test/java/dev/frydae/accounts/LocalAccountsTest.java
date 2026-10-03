package dev.frydae.accounts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LocalAccountsTest {
    @TempDir Path folder;

    @Test void permissionIsServerScopedPersistentAndEveryRequestHasANewRevision() throws Exception {
        assertNull(LocalAccounts.readPermission(folder, "server:first"));
        LocalAccounts.writePermission(folder, "server:first", true);
        var first = LocalAccounts.readPermission(folder, "server:first");
        assertTrue(first.paused());
        assertNull(LocalAccounts.readPermission(folder, "server:second"));
        assertNull(LocalAccounts.readPermission(folder, ""));
        LocalAccounts.writePermission(folder, "server:first", true);
        assertNotEquals(first.revision(), LocalAccounts.readPermission(folder, "server:first").revision());
        LocalAccounts.writePermission(folder, "server:first", false);
        assertFalse(LocalAccounts.readPermission(folder, "server:first").paused());
    }

    @Test void twoIndependentInstancesExchangeStatusAndSleepPermission() throws Exception {
        try (var main = new LocalAccounts(folder, false); var alt = new LocalAccounts(folder, true)) {
            main.publish("Main", "server:one", "Controller", "");
            alt.publish("Alt", "server:one", "Waiting for night", "");
            await(() -> main.peers().stream().anyMatch(p -> p.name().equals("Alt")));
            main.setPaused("server:one", true);
            await(() -> alt.permission("server:one") != null && alt.permission("server:one").paused());
            var permission = alt.permission("server:one");
            alt.publish("Alt", "server:one", "Sleep paused", permission.revision());
            await(() -> main.peers().stream().anyMatch(p -> p.revision().equals(permission.revision())));
            assertTrue(main.peers().getFirst().online(System.currentTimeMillis()));
            alt.publish("Alt", "server:two", "Waiting for night", "");
            await(() -> alt.ready("server:two"));
            assertNull(alt.permission("server:two"));
            assertNull(alt.permission("server:one"));
        }
    }

    @Test void corruptAndExpiredPeersDoNotBlockHealthyPeers() throws Exception {
        long now = System.currentTimeMillis();
        Path peers = folder.resolve("peers"); Files.createDirectories(peers);
        Files.writeString(peers.resolve("bad.properties"), "seen=not-a-number");
        Files.writeString(peers.resolve("old.properties"), "seen=" + (now - 86400001));
        Files.writeString(peers.resolve("crashed.properties"), "name=Crashed\nseen=" + (now - 10000));
        Files.writeString(peers.resolve("good.properties"), "name=Healthy\nseen=" + now);
        var result = LocalAccounts.readPeers(folder, now);
        assertEquals(2, result.size());
        assertFalse(result.get(0).online(now));
        assertTrue(result.get(1).online(now));
        assertFalse(Files.exists(peers.resolve("old.properties")));
    }

    @Test void malformedPermissionIsRejectedAndWorkerRecovers() throws Exception {
        LocalAccounts.writePermission(folder, "server:one", true);
        Path file;
        try (var files = Files.list(folder.resolve("sleep"))) { file = files.findFirst().orElseThrow(); }
        Files.writeString(file, "server=server:one\npaused=maybe\nrevision=invalid");
        assertThrows(java.io.IOException.class, () -> LocalAccounts.readPermission(folder, "server:one"));
        try (var main = new LocalAccounts(folder, false)) {
            main.publish("Main", "server:one", "Controller", "");
            await(() -> !main.error().isEmpty());
            main.setPaused("server:one", false);
            await(() -> main.permission("server:one") != null && main.error().isEmpty());
            assertFalse(main.permission("server:one").paused());
        }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(20);
        assertTrue(condition.getAsBoolean(), "Timed out waiting for local IPC");
    }
}
