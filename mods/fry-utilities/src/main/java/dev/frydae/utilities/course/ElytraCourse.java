package dev.frydae.utilities.course;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.frydae.utilities.FryUtilities;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** Client-only recorder and guide for one Nether route per world/server. */
public final class ElytraCourse {
    private static final Logger LOG = LoggerFactory.getLogger("dev.frydae.utilities.course");
    private static final double ENDPOINT_RADIUS_SQUARED = 14 * 14;
    private static final double MAX_TICK_JUMP_SQUARED = 12 * 12;
    private static final int MAX_FLIGHT_TICKS = 20 * 60 * 20;
    private static final int MAX_POINTS = 4_000;
    private static final int FORWARD_COLOR = 0xFF42E5FF;
    private static final int REVERSE_COLOR = 0xFFFFC857;
    private static CourseStore store;
    private static String context = "";
    private static long tick;
    private static List<CourseGraph.Point> recording;
    private static boolean recordingForward, displayForward = true;
    private static CourseGraph.Course forwardCourse, reverseCourse;
    private static Vec3 lastPosition;

    private ElytraCourse() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(ElytraCourse::tick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            literal("elytracourse").executes(c -> { status(); return 1; })
                .then(endpoint("a", true))
                .then(endpoint("b", false))
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
                recalculate();
            } catch (IOException ex) {
                LOG.error("Cannot open Elytra course", ex);
                say("Cannot open saved course; recording disabled for this world. See latest.log.");
            }
        }
        if (store == null || !FryUtilities.config().elytraCourseEnabled()) {
            recording = null; lastPosition = null; return;
        }
        tick++;
        var a = store.a(); var b = store.b();
        if (a == null || b == null) return;
        Vec3 position = client.player.position();
        var sample = new CourseGraph.Point(position.x(), position.y(), position.z(), tick);
        if (!client.player.isAlive() || !client.player.isFallFlying()) {
            recording = null; lastPosition = null;
            displayForward = a.distanceSquared(sample) <= b.distanceSquared(sample);
            return;
        }
        if (recording == null) {
            if (a.distanceSquared(sample) <= ENDPOINT_RADIUS_SQUARED) start(true, sample, position);
            else if (b.distanceSquared(sample) <= ENDPOINT_RADIUS_SQUARED) start(false, sample, position);
            else displayForward = a.distanceSquared(sample) <= b.distanceSquared(sample);
            return;
        }
        if (lastPosition != null && lastPosition.distanceToSqr(position) > MAX_TICK_JUMP_SQUARED) {
            recording = null; lastPosition = null;
            say("Flight discarded after a position jump."); return;
        }
        lastPosition = position;
        var last = recording.getLast();
        if (last.distanceSquared(sample) >= 2.5 * 2.5 || sample.tick() - last.tick() >= 10)
            recording.add(sample);
        if (recording.size() > MAX_POINTS || tick - recording.getFirst().tick() > MAX_FLIGHT_TICKS) {
            recording = null; lastPosition = null;
            say("Flight too long to record; course unchanged."); return;
        }
        CourseStore.Anchor target = recordingForward ? b : a;
        if (target.distanceSquared(sample) > ENDPOINT_RADIUS_SQUARED || recording.size() < 2
            || tick - recording.getFirst().tick() < 20) return;
        if (recording.getLast().tick() != tick) recording.add(sample);
        try {
            store.addFlight(recordingForward, new CourseGraph.Flight(recording));
            recalculate();
            var course = recordingForward ? forwardCourse : reverseCourse;
            say((recordingForward ? "A to B" : "B to A") + " flight saved. "
                + store.flights(recordingForward).size() + " runs; quickest inferred course "
                + seconds(course.estimatedTicks()) + "s.");
        } catch (IOException ex) {
            LOG.error("Cannot save Elytra flight", ex);
            say("Cannot save flight. See latest.log.");
        }
        recording = null; lastPosition = null;
        displayForward = !recordingForward;
    }

    private static void start(boolean forward, CourseGraph.Point sample, Vec3 position) {
        recordingForward = forward; displayForward = forward;
        recording = new ArrayList<>(); recording.add(sample); lastPosition = position;
        say("Recording " + (forward ? "A to B" : "B to A") + " Elytra flight.");
    }

    private static void setEndpoint(boolean first, CourseStore.Anchor specified) {
        var client = Minecraft.getInstance();
        if (store == null || client.player == null) { say("Join the Nether first."); return; }
        Vec3 position = client.player.position();
        var point = specified == null ? new CourseStore.Anchor(position.x(), position.y(), position.z()) : specified;
        var other = first ? store.b() : store.a();
        if (other != null && Math.sqrt(Math.pow(point.x() - other.x(), 2)
            + Math.pow(point.y() - other.y(), 2) + Math.pow(point.z() - other.z(), 2)) < 32) {
            say("Place endpoints at least 32 blocks apart."); return;
        }
        try {
            store.setAnchor(first, point);
            recording = null; lastPosition = null; recalculate();
            say("Point " + (first ? "A" : "B") + " set at " + coordinates(point)
                + ". Previous flights cleared because the endpoint changed.");
        } catch (IOException ex) {
            LOG.error("Cannot save Elytra course endpoint", ex);
            say("Cannot save endpoint. See latest.log.");
        }
    }

    private static void clearFlights() {
        if (store == null) { say("Join the Nether first."); return; }
        try {
            store.clearFlights(); recording = null; lastPosition = null; recalculate();
            say("Flights cleared; endpoints retained.");
        } catch (IOException ex) {
            LOG.error("Cannot clear Elytra course", ex);
            say("Cannot clear flights. See latest.log.");
        }
    }

    private static void setEnabled(boolean enabled) {
        var next = FryUtilities.config().copy();
        next.setElytraCourseEnabled(enabled);
        try {
            FryUtilities.applyConfig(next);
            if (!enabled) { recording = null; lastPosition = null; }
            say("Elytra course " + (enabled ? "enabled" : "disabled") + ".");
        } catch (IOException ex) { FryUtilities.reportConfigSaveFailure(ex); }
    }

    private static void status() {
        if (store == null) { say("Join the Nether to set or view a course."); return; }
        say("Course " + (FryUtilities.config().elytraCourseEnabled() ? "on" : "off")
            + "; A: " + coordinates(store.a()) + "; B: " + coordinates(store.b())
            + "; A to B: " + store.flights(true).size() + " runs, " + duration(forwardCourse)
            + "; B to A: " + store.flights(false).size() + " runs, " + duration(reverseCourse) + ".");
        say("Use /elytracourse a and /elytracourse b at each endpoint, or add x y z. Glide between them to record. /elytracourse clear removes flights.");
    }

    private static String duration(CourseGraph.Course course) {
        return course == null ? "no course" : seconds(course.estimatedTicks()) + "s estimated";
    }
    private static String seconds(double ticks) { return String.format(Locale.ROOT, "%.1f", ticks / 20); }
    private static String coordinates(CourseStore.Anchor a) {
        return a == null ? "unset" : String.format(Locale.ROOT, "%.1f %.1f %.1f", a.x(), a.y(), a.z());
    }

    private static void recalculate() {
        forwardCourse = CourseGraph.fastest(store.flights(true));
        reverseCourse = CourseGraph.fastest(store.flights(false));
    }
    private static void reset() {
        store = null; context = ""; recording = null; lastPosition = null;
        forwardCourse = null; reverseCourse = null; tick = 0;
    }

    /** Called during LevelRenderer's gizmo collection, once per rendered frame. */
    public static void draw() {
        var client = Minecraft.getInstance();
        if (store == null || client.level == null || client.player == null
            || !FryUtilities.config().elytraCourseEnabled()) return;
        var course = displayForward ? forwardCourse : reverseCourse;
        if (course == null) return;
        Vec3 camera = client.gameRenderer.mainCamera().position();
        List<CourseGraph.Point> points = course.points();
        int color = displayForward ? FORWARD_COLOR : REVERSE_COLOR;
        for (int i = 1; i < points.size(); i++) {
            var a = points.get(i - 1); var b = points.get(i);
            if (distanceSquared(camera, a) > 192 * 192 && distanceSquared(camera, b) > 192 * 192) continue;
            Gizmos.line(new Vec3(a.x(), a.y(), a.z()), new Vec3(b.x(), b.y(), b.z()), color, 2.5f);
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
