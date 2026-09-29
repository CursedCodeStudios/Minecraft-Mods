package dev.frydae.utilities.course;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.frydae.utilities.FryUtilities;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** Client-only recorder and guide for named Nether routes. */
public final class ElytraCourse {
    private static final Logger LOG = LoggerFactory.getLogger("dev.frydae.utilities.course");
    private static final double ENDPOINT_RADIUS_SQUARED = 5 * 5;
    private static final double MAX_TICK_JUMP_SQUARED = 12 * 12;
    private static final int MAX_FLIGHT_TICKS = 20 * 60 * 20;
    private static final int MAX_POINTS = 4_000;
    private static final int FORWARD_COLOR = 0xFF42E5FF;
    private static final int REVERSE_COLOR = 0xFFFFC857;
    private static CourseStore store;
    private static String context = "";
    private static long tick;
    private static List<CourseGraph.Point> recording;
    private static CourseProximity.Match recordingMatch;
    private static List<CourseProximity.Match> visible = List.of();
    private record Pair(CourseGraph.Course forward, CourseGraph.Course reverse) {
        CourseGraph.Course get(boolean fromA) { return fromA ? forward : reverse; }
    }
    private static final Map<String, Pair> courses = new HashMap<>();
    private static Vec3 lastPosition;

    private ElytraCourse() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(ElytraCourse::tick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            literal("elytracourse").executes(c -> { status(); return 1; })
                .then(endpoint("a", true))
                .then(endpoint("b", false))
                .then(literal("create").then(argument("name", StringArgumentType.string())
                    .executes(c -> { create(StringArgumentType.getString(c, "name")); return 1; })))
                .then(literal("select").then(argument("name", StringArgumentType.string())
                    .executes(c -> { select(StringArgumentType.getString(c, "name")); return 1; })))
                .then(literal("rename").then(argument("name", StringArgumentType.string())
                    .executes(c -> { rename(StringArgumentType.getString(c, "name")); return 1; })))
                .then(literal("label")
                    .then(literal("a").then(argument("name", StringArgumentType.string())
                        .executes(c -> { label(true, StringArgumentType.getString(c, "name")); return 1; })))
                    .then(literal("b").then(argument("name", StringArgumentType.string())
                        .executes(c -> { label(false, StringArgumentType.getString(c, "name")); return 1; }))))
                .then(literal("list").executes(c -> { list(); return 1; }))
                .then(literal("delete").executes(c -> { delete(); return 1; }))
                .then(literal("clear").executes(c -> { clearFlights(); return 1; }))
                .then(literal("on").executes(c -> { setEnabled(true); return 1; }))
                .then(literal("off").executes(c -> { setEnabled(false); return 1; }))
        ));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>
        endpoint(String name, boolean first) {
        return literal(name).executes(c -> { setEndpoint(first, null); return 1; })
            .then(argument("x", DoubleArgumentType.doubleArg(-29_999_999, 29_999_999))
                .then(argument("y", DoubleArgumentType.doubleArg(-2048, 2048))
                    .then(argument("z", DoubleArgumentType.doubleArg(-29_999_999, 29_999_999))
                        .executes(c -> {
                            setEndpoint(first, new CourseStore.Anchor(
                                DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "y"),
                                DoubleArgumentType.getDouble(c, "z")));
                            return 1;
                        }))));
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null ||
            !client.level.dimension().identifier().toString().equals("minecraft:the_nether")) {
            reset(); return;
        }
        String world;
        if (client.getSingleplayerServer() != null)
            world = "local:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                .toAbsolutePath().normalize();
        else if (client.getCurrentServer() != null)
            world = "server:" + client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        else { reset(); return; }
        if (!world.equals(context)) {
            reset(); context = world;
            try {
                store = new CourseStore(FabricLoader.getInstance().getConfigDir().resolve("fry-utilities"), world);
                recalculateAll();
            } catch (IOException ex) {
                LOG.error("Cannot open Elytra course", ex);
                say("Cannot open saved course; recording disabled for this world. See latest.log.");
            }
        }
        if (store == null || !FryUtilities.config().elytraCourseEnabled()) {
            cancelRecording(); visible = List.of(); return;
        }
        tick++;
        Vec3 position = client.player.position();
        var sample = new CourseGraph.Point(position.x(), position.y(), position.z(), tick);
        var near = CourseProximity.nearby(store.courses(), store.selected().name(), sample);
        if (!client.player.isAlive() || !client.player.isFallFlying()) {
            cancelRecording(); visible = near;
            return;
        }
        if (recording == null) {
            var match = near.stream().filter(candidate -> {
                var course = store.get(candidate.name());
                return course != null && course.a() != null && course.b() != null;
            }).findFirst().orElse(null);
            if (match != null) start(match, sample, position);
            else visible = near;
            return;
        }
        visible = List.of(recordingMatch);
        if (lastPosition != null && lastPosition.distanceToSqr(position) > MAX_TICK_JUMP_SQUARED) {
            cancelRecording(); visible = List.of();
            say("Flight discarded after a position jump."); return;
        }
        lastPosition = position;
        var last = recording.getLast();
        if (last.distanceSquared(sample) >= 2.5 * 2.5 || sample.tick() - last.tick() >= 10)
            recording.add(sample);
        if (recording.size() > MAX_POINTS || tick - recording.getFirst().tick() > MAX_FLIGHT_TICKS) {
            cancelRecording(); visible = List.of();
            say("Flight too long to record; course unchanged."); return;
        }
        var active = store.get(recordingMatch.name());
        if (active == null) { cancelRecording(); visible = List.of(); return; }
        CourseStore.Anchor target = recordingMatch.fromA() ? active.b() : active.a();
        if (target.distanceSquared(sample) > ENDPOINT_RADIUS_SQUARED || recording.size() < 2
            || tick - recording.getFirst().tick() < 20) return;
        if (recording.getLast().tick() != tick) recording.add(sample);
        try {
            store.addFlight(active.name(), recordingMatch.fromA(), new CourseGraph.Flight(recording));
            recalculate(active.name());
            var course = courses.get(key(active.name())).get(recordingMatch.fromA());
            say(active.name() + ": " + direction(active, recordingMatch.fromA()) + " flight saved. "
                + store.get(active.name()).flights(recordingMatch.fromA()).size()
                + " runs; quickest inferred course "
                + seconds(course.estimatedTicks()) + "s.");
        } catch (IOException ex) {
            LOG.error("Cannot save Elytra flight", ex);
            say("Cannot save flight. See latest.log.");
        }
        cancelRecording(); visible = near;
    }

    private static void start(CourseProximity.Match match, CourseGraph.Point sample, Vec3 position) {
        recordingMatch = match; visible = List.of(match);
        recording = new ArrayList<>(); recording.add(sample); lastPosition = position;
        say("Recording " + match.name() + ": "
            + direction(store.get(match.name()), match.fromA()) + " Elytra flight.");
    }

    private static void cancelRecording() {
        recording = null; recordingMatch = null; lastPosition = null;
    }

    private static void setEndpoint(boolean first, CourseStore.Anchor specified) {
        var client = Minecraft.getInstance();
        if (store == null || client.player == null) { say("Join the Nether first."); return; }
        var selected = store.selected();
        Vec3 position = client.player.position();
        var point = specified == null ? new CourseStore.Anchor(position.x(), position.y(), position.z()) : specified;
        var other = first ? selected.b() : selected.a();
        if (other != null && Math.sqrt(Math.pow(point.x() - other.x(), 2)
            + Math.pow(point.y() - other.y(), 2) + Math.pow(point.z() - other.z(), 2)) < 32) {
            say("Place endpoints at least 32 blocks apart."); return;
        }
        try {
            store.setAnchor(first, point);
            cancelRecording(); visible = List.of(); recalculate(selected.name());
            say(selected.name() + ": " + (first ? selected.aName() : selected.bName())
                + " set at " + coordinates(point)
                + ". Previous flights cleared because the endpoint changed.");
        } catch (IOException ex) {
            LOG.error("Cannot save Elytra course endpoint", ex);
            say("Cannot save endpoint. See latest.log.");
        }
    }

    private static void clearFlights() {
        if (store == null) { say("Join the Nether first."); return; }
        try {
            String name = store.selected().name();
            store.clearFlights(); cancelRecording(); visible = List.of(); recalculate(name);
            say(name + " flights cleared; endpoints retained.");
        } catch (IOException ex) {
            LOG.error("Cannot clear Elytra course", ex);
            say("Cannot clear flights. See latest.log.");
        }
    }

    private static void create(String name) {
        edit(() -> store.create(name), "Created and selected course " + name + ". Set its endpoints with /elytracourse a and b.",
            "That course name is already used, or the 32-course limit was reached.");
    }
    private static void select(String name) {
        edit(() -> store.select(name), "Selected course " + name + ".", "No course named " + name + ".");
    }
    private static void rename(String name) {
        if (store == null) { say("Join the Nether first."); return; }
        String old = store.selected().name();
        edit(() -> store.rename(old, name), "Renamed course " + old + " to " + name + ".",
            "That course name is already used.");
    }
    private static void label(boolean first, String name) {
        edit(() -> store.renameEndpoint(first, name),
            "Renamed " + (first ? "first" : "second") + " endpoint to " + name + ".",
            "Both endpoints need different names.");
    }
    private static void delete() {
        if (store == null) { say("Join the Nether first."); return; }
        String name = store.selected().name();
        edit(() -> store.delete(name), "Deleted course " + name + ".",
            "Keep at least one course; use /elytracourse clear to remove its flights.");
    }
    private static void list() {
        if (store == null) { say("Join the Nether first."); return; }
        say("Courses: " + String.join(", ", store.names())
            + ". Selected: " + store.selected().name() + ". A line appears within five blocks of an endpoint.");
    }
    @FunctionalInterface private interface Edit { boolean run() throws IOException; }
    private static void edit(Edit change, String success, String failure) {
        if (store == null) { say("Join the Nether first."); return; }
        try {
            if (!change.run()) { say(failure); return; }
            cancelRecording(); visible = List.of(); recalculateAll(); say(success);
        } catch (IllegalArgumentException ex) { say(ex.getMessage()); }
        catch (IOException ex) {
            LOG.error("Cannot save Elytra course changes", ex);
            say("Cannot save course change. See latest.log.");
        }
    }

    private static void setEnabled(boolean enabled) {
        var next = FryUtilities.config().copy();
        next.setElytraCourseEnabled(enabled);
        try {
            FryUtilities.applyConfig(next);
            if (!enabled) { cancelRecording(); visible = List.of(); }
            say("Elytra course " + (enabled ? "enabled" : "disabled") + ".");
        } catch (IOException ex) { FryUtilities.reportConfigSaveFailure(ex); }
    }

    private static void status() {
        if (store == null) { say("Join the Nether to set or view a course."); return; }
        var selected = store.selected(); var pair = courses.get(key(selected.name()));
        say("Selected " + selected.name() + " (" + (FryUtilities.config().elytraCourseEnabled() ? "on" : "off")
            + "); " + selected.aName() + ": " + coordinates(selected.a())
            + "; " + selected.bName() + ": " + coordinates(selected.b()) + ".");
        say(direction(selected, true) + ": " + selected.flights(true).size() + " runs, "
            + duration(pair == null ? null : pair.forward()) + "; " + direction(selected, false)
            + ": " + selected.flights(false).size() + " runs, "
            + duration(pair == null ? null : pair.reverse()) + ".");
        say("Use /elytracourse list, create <name>, select <name>, rename <name>, label a|b <name>, a|b [x y z], clear, or delete.");
    }

    private static String direction(CourseStore.Course course, boolean fromA) {
        return fromA ? course.aName() + " to " + course.bName()
            : course.bName() + " to " + course.aName();
    }

    private static String duration(CourseGraph.Course course) {
        return course == null ? "no course" : seconds(course.estimatedTicks()) + "s estimated";
    }
    private static String seconds(double ticks) { return String.format(Locale.ROOT, "%.1f", ticks / 20); }
    private static String coordinates(CourseStore.Anchor a) {
        return a == null ? "unset" : String.format(Locale.ROOT, "%.1f %.1f %.1f", a.x(), a.y(), a.z());
    }

    private static void recalculate(String name) {
        var course = store.get(name);
        if (course == null) { courses.remove(key(name)); return; }
        courses.put(key(name), new Pair(CourseGraph.fastest(course.flights(true)),
            CourseGraph.fastest(course.flights(false))));
    }
    private static void recalculateAll() {
        courses.clear();
        for (var course : store.courses()) recalculate(course.name());
    }
    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
    private static void reset() {
        store = null; context = ""; cancelRecording(); visible = List.of();
        courses.clear(); tick = 0;
    }

    /** Called during LevelRenderer's gizmo collection, once per rendered frame. */
    public static void draw() {
        var client = Minecraft.getInstance();
        if (store == null || client.level == null || client.player == null
            || !FryUtilities.config().elytraCourseEnabled()) return;
        Vec3 camera = client.gameRenderer.mainCamera().position();
        for (var shown : visible) {
            var pair = courses.get(key(shown.name()));
            var course = pair == null ? null : pair.get(shown.fromA());
            if (course == null) continue;
            List<CourseGraph.Point> points = course.points();
            int color = shown.fromA() ? FORWARD_COLOR : REVERSE_COLOR;
            for (int i = 1; i < points.size(); i++) {
                var a = points.get(i - 1); var b = points.get(i);
                if (distanceSquared(camera, a) > 192 * 192 && distanceSquared(camera, b) > 192 * 192) continue;
                Gizmos.line(new Vec3(a.x(), a.y(), a.z()), new Vec3(b.x(), b.y(), b.z()), color, 2.5f);
            }
            var named = store.get(shown.name());
            if (named == null) continue;
            var endpoint = shown.fromA() ? named.a() : named.b();
            if (endpoint != null && camera.distanceToSqr(new Vec3(endpoint.x(), endpoint.y(), endpoint.z())) < 32 * 32)
                Gizmos.billboardText(named.name() + " — " + (shown.fromA() ? named.aName() : named.bName()),
                    new Vec3(endpoint.x(), endpoint.y() + 2, endpoint.z()), TextGizmo.Style.forColorAndCentered(color));
        }
    }
    private static double distanceSquared(Vec3 camera, CourseGraph.Point point) {
        double dx = camera.x() - point.x(), dy = camera.y() - point.y(), dz = camera.z() - point.z();
        return dx * dx + dy * dy + dz * dz;
    }
    private static void say(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal("[Elytra Course] " + text));
    }
}
