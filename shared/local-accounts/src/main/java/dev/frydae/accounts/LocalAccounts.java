package dev.frydae.accounts;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Local-only IPC. Immutable snapshots cross threads; all disk work runs on one worker. */
public final class LocalAccounts implements AutoCloseable {
    public record Permission(String revision, boolean paused) {}
    public record Peer(String id, String name, String server, String status, long seen, String revision) {
        public boolean online(long now) { return now - seen < 6000 && seen <= now + 6000; }
    }
    private record Presence(String name, String server, String status, String revision) {}
    private record Control(String server, Permission permission) {}
    private final Path folder;
    private final String id = UUID.randomUUID().toString();
    private final boolean alt;
    private final ScheduledExecutorService worker;
    private volatile Presence presence = new Presence("Starting", "", "Disconnected", "");
    private volatile Control control = new Control("", null);
    private volatile List<Peer> peers = List.of();
    private volatile String pollError = "";
    private volatile String commandError = "";

    public LocalAccounts(boolean alt) {
        this(Path.of(System.getProperty("user.home"), ".fry-utilities", "local-accounts"), alt);
    }
    public LocalAccounts(Path folder, boolean alt) {
        this.folder = folder;
        this.alt = alt;
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Fry local accounts"); thread.setDaemon(true); return thread;
        });
        worker.scheduleWithFixedDelay(this::poll, 0, 1, TimeUnit.SECONDS);
    }
    public void publish(String name, String server, String status, String revision) {
        presence = new Presence(name, server, status, revision);
    }
    public Permission permission(String server) {
        Control snapshot = control;
        return server.equals(snapshot.server()) ? snapshot.permission() : null;
    }
    public List<Peer> peers() { return peers; }
    public String error() { return commandError.isEmpty() ? pollError : commandError; }
    public boolean ready(String server) { return server.equals(control.server()); }
    public Path folder() { return folder; }
    public void setPaused(String server, boolean paused) {
        if (server.isEmpty()) return;
        worker.execute(() -> {
            try {
                writePermission(folder, server, paused);
                commandError = "";
            } catch (IOException ex) { commandError = "Cannot save local sleep control: " + ex.getMessage(); }
        });
    }
    private void poll() {
        try {
            Presence current = presence;
            if (alt) {
                Properties p = new Properties();
                p.setProperty("name", current.name()); p.setProperty("server", current.server());
                p.setProperty("status", current.status()); p.setProperty("revision", current.revision());
                p.setProperty("seen", Long.toString(System.currentTimeMillis()));
                write(folder.resolve("peers").resolve(id + ".properties"), p);
            }
            Permission next = readPermission(folder, current.server());
            control = new Control(current.server(), next);
            peers = readPeers(folder, System.currentTimeMillis());
            pollError = "";
        } catch (IOException | IllegalArgumentException ex) {
            pollError = "Local account communication failed: " + ex.getMessage();
        }
    }
    static void writePermission(Path folder, String server, boolean paused) throws IOException {
        Properties p = new Properties();
        p.setProperty("server", server); p.setProperty("paused", Boolean.toString(paused));
        p.setProperty("revision", UUID.randomUUID().toString());
        write(folder.resolve("sleep").resolve(key(server) + ".properties"), p);
    }
    static Permission readPermission(Path folder, String server) throws IOException {
        if (server.isEmpty()) return null;
        Path file = folder.resolve("sleep").resolve(key(server) + ".properties");
        if (!Files.exists(file)) return null;
        Properties p = read(file);
        String value = p.getProperty("paused", "");
        String revision = p.getProperty("revision", "");
        if (!server.equals(p.getProperty("server")) || revision.isBlank()
            || !(value.equals("true") || value.equals("false"))) throw new IOException("Invalid sleep control");
        return new Permission(revision, Boolean.parseBoolean(value));
    }
    static List<Peer> readPeers(Path folder, long now) throws IOException {
        Path dir = folder.resolve("peers");
        if (!Files.isDirectory(dir)) return List.of();
        List<Peer> result = new ArrayList<>();
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".properties")).limit(256).toList()) {
                try {
                    Properties p = read(file);
                    long seen = Long.parseLong(p.getProperty("seen", "0"));
                    if (seen < now - 86400000) { Files.deleteIfExists(file); continue; }
                    result.add(new Peer(file.getFileName().toString(), p.getProperty("name", "Unknown"),
                        p.getProperty("server", ""), p.getProperty("status", "Unknown"), seen, p.getProperty("revision", "")));
                } catch (IOException | IllegalArgumentException ignored) { /* A broken peer cannot block the others. */ }
            }
        }
        result.sort(Comparator.comparing(Peer::name).thenComparing(Peer::id));
        return List.copyOf(result);
    }
    private static Properties read(Path file) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(16385); }
        if (bytes.length > 16384) throw new IOException("Local account file too large");
        Properties p = new Properties(); p.load(new StringReader(new String(bytes, StandardCharsets.UTF_8))); return p;
    }
    private static void write(Path file, Properties p) throws IOException {
        Files.createDirectories(file.getParent());
        StringWriter text = new StringWriter(); p.store(text, "Fry local accounts v1");
        Path temp = Files.createTempFile(file.getParent(), "write-", ".tmp");
        try {
            Files.writeString(temp, text.toString(), StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
    private static String key(String server) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(server.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    @Override public void close() { worker.shutdownNow(); }
}
