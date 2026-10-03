package dev.frydae.utilities.course;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CourseTransferTest {
    @TempDir Path folder;

    private CourseStore source() throws Exception {
        var store = new CourseStore(folder.resolve("main"), "server:example");
        store.rename("Default", "Hub to Farm");
        store.renameEndpoint(true, "Nether Hub");
        store.renameEndpoint(false, "Gold Farm");
        store.setAnchor(true, new CourseStore.Anchor(0, 70, 0));
        store.setAnchor(false, new CourseStore.Anchor(100, 75, 0));
        store.addFlight("Hub to Farm", true, new CourseGraph.Flight(List.of(
            new CourseGraph.Point(0, 70, 0, 1), new CourseGraph.Point(40, 73, 4, 21),
            new CourseGraph.Point(100, 75, 0, 61))));
        store.addFlight("Hub to Farm", false, new CourseGraph.Flight(List.of(
            new CourseGraph.Point(100, 75, 0, 71), new CourseGraph.Point(50, 73, 5, 101),
            new CourseGraph.Point(0, 70, 0, 141))));
        return store;
    }

    @Test void transfersLabelsBothDirectionsAndInferredCourseBetweenInstances() throws Exception {
        var source = source();
        var files = new CourseTransfers(folder.resolve("shared"));
        var file = files.exportCourse(source, "Hub to Farm");
        var destination = new CourseStore(folder.resolve("alt"), "server:example");
        var imported = files.importCourse(destination, file.getFileName().toString(), null);

        assertEquals("Hub to Farm", imported.name());
        assertEquals(source.selected().a(), imported.a());
        assertEquals(source.selected().b(), imported.b());
        assertEquals("Nether Hub", imported.aName());
        assertEquals("Gold Farm", imported.bName());
        assertEquals(source.selected().flights(true), imported.flights(true));
        assertEquals(source.selected().flights(false), imported.flights(false));
        assertEquals(CourseGraph.fastest(source.selected().flights(true)), CourseGraph.fastest(imported.flights(true)));
        var reopened = new CourseStore(folder.resolve("alt"), "server:example");
        assertEquals(imported.name(), reopened.selected().name());
        assertEquals(imported.flights(false), reopened.selected().flights(false));
        assertNull(new CourseStore(folder.resolve("alt"), "server:other").get(imported.name()));
        assertTrue(files.filenames().contains(file.getFileName().toString()));
    }

    @Test void duplicatesAndExplicitNamesNeverOverwriteExistingRoutesOrExportFiles() throws Exception {
        var source = source();
        var files = new CourseTransfers(folder.resolve("shared"));
        Path first = files.exportCourse(source, "Hub to Farm");
        Path second = files.exportCourse(source, "Hub to Farm");
        assertNotEquals(first, second);
        assertEquals(Files.readString(first), Files.readString(second));
        var destination = new CourseStore(folder.resolve("alt"), "server:example");
        destination.rename("Default", "Hub to Farm");
        destination.setAnchor(true, new CourseStore.Anchor(500, 70, 0));
        assertEquals("Hub to Farm 2", files.importCourse(destination, first.toString(), null).name());
        assertEquals(500, destination.get("Hub to Farm").a().x());
        assertEquals("Alt Route", files.importCourse(destination, first.toString(), "Alt Route").name());
        assertThrows(IllegalArgumentException.class,
            () -> files.importCourse(destination, first.toString(), "hub TO farm"));
        assertEquals("Alt Route", destination.selected().name());
    }

    @Test void rejectsMalformedWrongDimensionAndInvalidFlightsWithoutChangingStore() throws Exception {
        var source = source();
        String json = source.exportCourse("Hub to Farm");
        var destination = new CourseStore(folder.resolve("alt"), "server:example");
        assertThrows(IOException.class, () -> destination.importCourse("{}", null));
        var document = JsonParser.parseString(json).getAsJsonObject();
        document.addProperty("dimension", "minecraft:overworld");
        assertThrows(IOException.class, () -> destination.importCourse(document.toString(), null));
        document.addProperty("dimension", "minecraft:the_nether");
        document.getAsJsonObject("course").getAsJsonArray("forward").get(0).getAsJsonObject()
            .getAsJsonArray("points").get(1).getAsJsonObject().addProperty("tick", 0);
        assertThrows(IOException.class, () -> destination.importCourse(document.toString(), null));
        assertEquals(List.of("Default"), destination.names());
        assertNull(destination.selected().a());
        assertThrows(IllegalArgumentException.class, () -> destination.exportCourse("Default"));
    }

    @Test void courseLimitAndOversizedFilesLeaveExistingDataIntact() throws Exception {
        var source = source();
        String json = source.exportCourse("Hub to Farm");
        var destination = new CourseStore(folder.resolve("alt"), "server:example");
        for (int i = 1; i < 32; i++) assertTrue(destination.create("Route " + i));
        assertThrows(IllegalArgumentException.class, () -> destination.importCourse(json, null));
        assertEquals(32, destination.names().size());
        Path oversized = folder.resolve("large.json");
        try (var file = new RandomAccessFile(oversized.toFile(), "rw")) { file.setLength(16 * 1024 * 1024 + 1); }
        var transfers = new CourseTransfers(folder);
        assertThrows(IOException.class, () -> transfers.importCourse(destination, "large.json", null));
        assertEquals("Route 31", destination.selected().name());
    }
}
