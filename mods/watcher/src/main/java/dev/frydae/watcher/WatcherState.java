package dev.frydae.watcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.Properties;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

/** Stores each world's chat pause and manual switch across reconnects. */
public final class WatcherState {
    private final Path file;
    private final Properties values = new Properties();

    public WatcherState(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            try (var input = Files.newInputStream(file)) { values.load(input); }
            catch (IllegalArgumentException ex) { throw new IOException("Invalid Watcher state", ex); }
        }
    }

    public boolean paused(String context) { return Boolean.parseBoolean(values.getProperty(key(context) + ".paused", "false")); }
    public boolean enabled(String context) { return Boolean.parseBoolean(values.getProperty(key(context) + ".enabled", "true")); }
    public void setPaused(String context, boolean paused) throws IOException { save(key(context) + ".paused", paused); }
    public void setEnabled(String context, boolean enabled) throws IOException { save(key(context) + ".enabled", enabled); }
    public boolean autoReconnect() { return Boolean.parseBoolean(values.getProperty("autoReconnect", "true")); }
    public void setAutoReconnect(boolean enabled) throws IOException { save("autoReconnect", enabled); }

    private void save(String key, boolean value) throws IOException {
        values.setProperty(key, Boolean.toString(value));
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (var output = Files.newOutputStream(temporary)) { values.store(output, "Watcher world settings"); }
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String key(String context) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(context.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
