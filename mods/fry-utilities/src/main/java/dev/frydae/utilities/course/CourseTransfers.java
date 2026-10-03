package dev.frydae.utilities.course;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Portable course files shared by Minecraft instances under the same OS account. */
public final class CourseTransfers {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private final Path folder;

    public CourseTransfers(Path folder) { this.folder = folder.toAbsolutePath().normalize(); }
    public static Path sharedFolder() {
        return Path.of(System.getProperty("user.home"), ".fry-utilities", "elytra-courses");
    }
    public Path folder() { return folder; }

    public Path exportCourse(CourseStore store, String name) throws IOException {
        String json = store.exportCourse(name);
        String slug = store.get(name).name().toLowerCase(Locale.ROOT).replace(' ', '_');
        Path file = folder.resolve(slug + "-" + UUID.randomUUID() + ".json");
        Files.createDirectories(folder);
        Files.writeString(file, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return file;
    }

    public CourseStore.Course importCourse(CourseStore store, String filename, String newName) throws IOException {
        Path file = Path.of(filename);
        if (!file.isAbsolute()) file = folder.resolve(file);
        byte[] bytes;
        try (var input = Files.newInputStream(file.normalize())) { bytes = input.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new IOException("Course export exceeds the 16 MiB limit");
        return store.importCourse(new String(bytes, StandardCharsets.UTF_8), newName);
    }

    public List<String> filenames() {
        if (!Files.isDirectory(folder)) return List.of();
        try (var files = Files.list(folder)) {
            return files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".json")).sorted().limit(256).toList();
        } catch (IOException ex) { return List.of(); }
    }
}
