package dev.seedscout;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.concurrent.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class SeedScout implements ClientModInitializer {
    private static final int MAX_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Seed Scout cracker");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Future<?> task;

    @Override public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            literal("seedscout")
                .executes(context -> { status(); return 1; })
                .then(literal("crack")
                    .executes(context -> start(MAX_THREADS))
                    .then(argument("threads", IntegerArgumentType.integer(1, MAX_THREADS))
                        .executes(context -> start(IntegerArgumentType.getInteger(context, "threads")))))
        ));
    }

    private static int start(int threads) {
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null) { say("Join a world first.", ChatFormatting.RED); return 0; }
        if (!client.level.dimension().identifier().toString().equals("minecraft:the_nether")) {
            say("Enter the Nether before cracking.", ChatFormatting.RED); return 0;
        }
        if (task != null && !task.isDone()) { say("A seed crack is already running.", ChatFormatting.RED); return 0; }

        var chunk = client.player.chunkPosition();
        final java.util.List<BedrockObservation> observations;
        try { observations = ObservationScanner.scan(client.level, chunk.x(), chunk.z()); }
        catch (IllegalStateException ex) { say(ex.getMessage(), ChatFormatting.RED); return 0; }
        long biomeZoomSeed = client.level.getBiomeManager().biomeZoomSeed;
        say("Cracking from " + observations.size() + " roof and floor bedrock observations using " + threads + " threads.", ChatFormatting.LIGHT_PURPLE);

        task = EXECUTOR.submit(() -> {
            try {
                long[] structureSeeds = NativeCracker.crack(observations, threads);
                if (structureSeeds.length == 0) {
                    say("No matching seed found. Move to newly generated vanilla Nether chunks and try again.", ChatFormatting.RED);
                    return;
                }
                var worldSeeds = WorldSeedResolver.resolve(structureSeeds, biomeZoomSeed);
                if (worldSeeds.isEmpty()) {
                    say("Found " + structureSeeds.length + " structure-seed candidate(s), but none matched this world's biome hash.", ChatFormatting.YELLOW);
                    for (long seed : structureSeeds) seedLine("Structure seed", seed);
                    return;
                }
                say("Recovered " + worldSeeds.size() + " full world seed" + (worldSeeds.size() == 1 ? "." : "s."), ChatFormatting.GREEN);
                for (long seed : worldSeeds) seedLine("World seed", seed);
            } catch (Throwable ex) {
                say("Seed cracking failed: " + ex.getClass().getSimpleName() + ". See latest.log.", ChatFormatting.RED);
                org.slf4j.LoggerFactory.getLogger("seedscout").error("Seed cracking failed", ex);
            }
        });
        return 1;
    }

    private static void status() {
        boolean running = task != null && !task.isDone();
        say("Status: " + (running ? "cracking" : "ready") + ". Use /seedscout crack [threads] in the Nether; maximum threads: " + MAX_THREADS + ".", ChatFormatting.LIGHT_PURPLE);
    }

    private static void seedLine(String label, long seed) {
        var value = ComponentUtils.copyOnClickText(Long.toString(seed)).withStyle(ChatFormatting.AQUA);
        send(Component.literal(label + ": ").append(value));
    }

    private static void say(String text, ChatFormatting color) {
        send(Component.literal(text).withStyle(color));
    }

    private static void send(Component body) {
        var message = Component.literal("[Seed Scout] ").withStyle(ChatFormatting.DARK_PURPLE).append(body);
        Minecraft.getInstance().schedule(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(message);
        });
    }
}
