package dev.frydae.utilities.container;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.frydae.utilities.FryUtilities;
import dev.frydae.utilities.mixin.ContainerScreenAccessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Adds a compact JSON export control to storage-container screens. */
public final class ContainerExporter {
    private static final Logger LOG = LoggerFactory.getLogger("dev.frydae.utilities.container");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(ZoneId.systemDefault());
    private static final int BUTTON_SIZE = 11;

    private ContainerExporter() { }

    public static void initialize() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)
                || !isStorageMenu(containerScreen.getMenu())) return;
            var added = new boolean[] { false };
            // Wait one screen tick so Inventory Profiles Next and other UI mods
            // have finished adding their controls before choosing our position.
            ScreenEvents.afterTick(screen).register(current -> {
                if (!added[0]) {
                    added[0] = true;
                    addButton(current);
                }
            });
        });
    }

    private static void addButton(Screen screen) {
        if (!FryUtilities.config().containerExportButton()
            || !(screen instanceof AbstractContainerScreen<?> containerScreen)
            || !isStorageMenu(containerScreen.getMenu())) return;
        var position = (ContainerScreenAccessor)screen;
        int left = position.fryutilities$getLeftPos();
        int top = position.fryutilities$getTopPos();
        int imageWidth = position.fryutilities$getImageWidth();
        int firstPlayerSlotY = containerScreen.getMenu().slots.stream()
            .filter(slot -> slot.container instanceof Inventory)
            .mapToInt(slot -> slot.y).min().orElse(84);
        int inventoryLabelWidth = Minecraft.getInstance().font.width(
            Component.translatable("container.inventory"));
        int x = Math.min(left + 8 + inventoryLabelWidth + 5,
            left + imageWidth - BUTTON_SIZE - 8);
        int y = top + firstPlayerSlotY - 14;
        var button = Button.builder(Component.literal("J"), ignored -> export(containerScreen))
            .bounds(x, y, BUTTON_SIZE, BUTTON_SIZE).build();
        button.setTooltip(Tooltip.create(Component.literal("Export container contents to JSON")));
        Screens.getWidgets(screen).add(button);
    }

    private static boolean isStorageMenu(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu
            || menu instanceof HopperMenu || menu instanceof DispenserMenu;
    }

    private static void export(AbstractContainerScreen<?> screen) {
        try {
            List<Slot> slots = screen.getMenu().slots.stream()
                .filter(slot -> !(slot.container instanceof Inventory)).toList();
            Instant now = Instant.now();
            JsonObject document = createDocument(screen.getTitle().getString(), slots, now);
            Path folder = FabricLoader.getInstance().getConfigDir()
                .resolve("fry-utilities").resolve("container-exports");
            Files.createDirectories(folder);
            String title = safeName(screen.getTitle().getString());
            Path file = folder.resolve(FILE_TIME.format(now) + "-" + title + ".json").toAbsolutePath().normalize();
            writeAtomically(file, JSON.toJson(document));
            showSuccess(file, document.get("occupiedSlotCount").getAsInt());
        } catch (IOException | RuntimeException ex) {
            LOG.error("Cannot export container contents", ex);
            var player = Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(Component.literal(
                "[Fry Utilities] Cannot export this container. See latest.log.").withStyle(ChatFormatting.RED));
        }
    }

    static JsonObject createDocument(String title, List<Slot> slots, Instant exportedAt) {
        var root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("container", title);
        root.addProperty("exportedAt", exportedAt.toString());
        root.addProperty("slotIndexBase", 0);
        root.addProperty("containerSlotCount", slots.size());
        var items = new JsonArray();
        int totalItems = 0;
        for (var slot : slots) {
            if (!slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            items.add(itemJson(slot.getContainerSlot(), stack));
            totalItems += stack.getCount();
        }
        root.addProperty("occupiedSlotCount", items.size());
        root.addProperty("totalItemCount", totalItems);
        root.add("items", items);
        return root;
    }

    static JsonObject itemJson(int slot, ItemStack stack) {
        var item = new JsonObject();
        item.addProperty("slot", slot);
        item.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        item.addProperty("name", stack.getHoverName().getString());
        item.addProperty("baseName", stack.getItemName().getString());
        item.addProperty("customName", stack.has(DataComponents.CUSTOM_NAME));
        item.addProperty("count", stack.getCount());
        item.addProperty("componentCount", stack.getComponents().size());

        var details = new ArrayList<EnchantmentDetail>();
        addEnchantments(details, stack.getEnchantments(), "applied");
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) addEnchantments(details, stored, "stored");
        details.sort(Comparator.comparing(EnchantmentDetail::id).thenComparing(EnchantmentDetail::source));
        var enchantments = new JsonArray();
        for (var detail : details) {
            var enchantment = new JsonObject();
            enchantment.addProperty("id", detail.id());
            enchantment.addProperty("name", detail.name());
            enchantment.addProperty("level", detail.level());
            enchantment.addProperty("source", detail.source());
            enchantments.add(enchantment);
        }
        item.add("enchantments", enchantments);

        if (stack.isDamageableItem()) {
            int maximum = stack.getMaxDamage();
            int damage = stack.getDamageValue();
            int remaining = Math.max(0, maximum - damage);
            var durability = new JsonObject();
            durability.addProperty("remaining", remaining);
            durability.addProperty("maximum", maximum);
            durability.addProperty("damage", damage);
            durability.addProperty("percent", maximum == 0 ? 0
                : Math.round(remaining * 10000.0 / maximum) / 100.0);
            item.add("durability", durability);
        } else item.add("durability", JsonNull.INSTANCE);
        return item;
    }

    private static void addEnchantments(List<EnchantmentDetail> result, ItemEnchantments enchantments,
        String source) {
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            int level = enchantments.getLevel(enchantment);
            String id = enchantment.unwrapKey().map(key -> key.identifier().toString())
                .orElseGet(enchantment::getRegisteredName);
            result.add(new EnchantmentDetail(id, Enchantment.getFullname(enchantment, level).getString(),
                level, source));
        }
    }

    private static String safeName(String title) {
        String safe = title.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-")
            .replaceAll("^-+|-+$", "");
        return safe.isEmpty() ? "container" : safe.substring(0, Math.min(48, safe.length()));
    }

    private static void writeAtomically(Path file, String content) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void showSuccess(Path file, int occupiedSlots) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var link = Component.literal("[Open JSON]").withStyle(style -> style
            .withColor(ChatFormatting.AQUA).withUnderlined(true)
            .withClickEvent(new ClickEvent.OpenFile(file))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(file.toString()))));
        player.sendSystemMessage(Component.literal("[Fry Utilities] Exported " + occupiedSlots
            + (occupiedSlots == 1 ? " occupied slot. " : " occupied slots. ")).append(link));
    }

    private record EnchantmentDetail(String id, String name, int level, String source) { }
}
