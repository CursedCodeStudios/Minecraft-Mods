package dev.frydae.utilities.container;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContainerExporterTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bind(Items.DIAMOND_PICKAXE, DataComponentMap.builder()
            .set(DataComponents.MAX_STACK_SIZE, 1)
            .set(DataComponents.MAX_DAMAGE, 1561)
            .set(DataComponents.DAMAGE, 0)
            .set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).build());
        bind(Items.ENCHANTED_BOOK, DataComponentMap.builder()
            .set(DataComponents.MAX_STACK_SIZE, 1)
            .set(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).build());
    }

    @Test void exportsItemIdentityEnchantmentsAndDurability() {
        var stack = stack(Items.DIAMOND_PICKAXE);
        stack.setDamageValue(183);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment("Efficiency", 5), 5);
        enchantments.set(enchantment("Unbreaking", 3), 3);
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());

        var json = ContainerExporter.itemJson(7, stack);
        assertEquals(7, json.get("slot").getAsInt());
        assertEquals("minecraft:diamond_pickaxe", json.get("id").getAsString());
        assertEquals(2, json.getAsJsonArray("enchantments").size());
        assertTrue(json.getAsJsonArray("enchantments").toString().contains("Efficiency"));
        assertTrue(json.getAsJsonArray("enchantments").toString().contains("Unbreaking"));
        assertEquals(stack.getMaxDamage() - 183,
            json.getAsJsonObject("durability").get("remaining").getAsInt());
        assertEquals(stack.getMaxDamage(),
            json.getAsJsonObject("durability").get("maximum").getAsInt());
    }

    @Test void exportsStoredEnchantmentsAndNullDurability() {
        var stack = stack(Items.ENCHANTED_BOOK);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(enchantment("Mending", 1), 1);
        stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());

        var json = ContainerExporter.itemJson(0, stack);
        var exported = json.getAsJsonArray("enchantments").get(0).getAsJsonObject();
        assertEquals("Mending", exported.get("name").getAsString());
        assertEquals(1, exported.get("level").getAsInt());
        assertEquals("stored", exported.get("source").getAsString());
        assertTrue(json.get("durability").isJsonNull());
    }

    private static Holder<Enchantment> enchantment(String name, int maxLevel) {
        HolderSet<Item> items = HolderSet.direct(Holder.direct(Items.DIAMOND_PICKAXE));
        var definition = Enchantment.definition(items, 1, maxLevel,
            Enchantment.constantCost(1), Enchantment.constantCost(1), 1, EquipmentSlotGroup.MAINHAND);
        return Holder.direct(new Enchantment(Component.literal(name), definition,
            HolderSet.empty(), DataComponentMap.EMPTY));
    }

    private static ItemStack stack(Item item) {
        return new ItemStack(BuiltInRegistries.ITEM.wrapAsHolder(item));
    }

    @SuppressWarnings("unchecked")
    private static void bind(Item item, DataComponentMap components) {
        ((Holder.Reference<Item>)BuiltInRegistries.ITEM.wrapAsHolder(item)).bindComponents(components);
    }
}
