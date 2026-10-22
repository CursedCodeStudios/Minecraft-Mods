package dev.frydae.endcity;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import dev.frydae.endcity.CitySignature.*;

class CitySignatureTest {
    @Test void signatureFixturesMatchTheCurrentGamesShippedTemplates() throws Exception {
        for (String name : List.of("base_floor", "ship")) {
            try (var input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/data/minecraft/structure/end_city/" + name + ".nbt"))) {
                var template = net.minecraft.nbt.NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                var palette = template.getListOrEmpty("palette");
                assertFalse(palette.isEmpty(), "Missing palette: " + template.keySet());
                var actual = new HashMap<Pos, Block>();
                for (var tag : template.getListOrEmpty("blocks")) {
                    var block = (net.minecraft.nbt.CompoundTag)tag;
                    var pos = block.getListOrEmpty("pos");
                    var paletteEntry = palette.getCompoundOrEmpty(block.getIntOr("state", -1));
                    String type = paletteEntry.getStringOr("id", "");
                    assertFalse(type.isEmpty(), "Unknown palette entry: " + palette.get(block.getIntOr("state", -1)));
                    Block value = switch (type) {
                        case "minecraft:purpur_block" -> Block.PURPUR;
                        case "minecraft:purpur_pillar" -> Block.PILLAR;
                        case "minecraft:end_stone_bricks" -> Block.BRICKS;
                        case "minecraft:magenta_stained_glass" -> Block.GLASS;
                        case "minecraft:obsidian" -> Block.OBSIDIAN;
                        default -> Block.OTHER;
                    };
                    actual.put(new Pos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0)), value);
                }
                var expected = fixture(name, new Pos(0, 0, 0), 0);
                for (var entry : expected.entrySet())
                    assertEquals(entry.getValue(), actual.get(entry.getKey()), name + " at " + entry.getKey());
            }
        }
    }
    // Fixtures are block positions/types extracted from the shipped 26.2 NBT templates,
    // independently of the samples in the detector. Loot and entities are omitted.
    private Map<Pos, Block> fixture(String name, Pos origin, int rotation) throws Exception {
        var blocks = new HashMap<Pos, Block>();
        try (var input = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/" + name + ".csv")), StandardCharsets.UTF_8))) {
            for (String line; (line = input.readLine()) != null;) {
                var fields = line.split(",");
                var p = origin.offset(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), rotation);
                Block block = switch (fields[3]) {
                    case "purpur_block" -> Block.PURPUR;
                    case "purpur_pillar" -> Block.PILLAR;
                    case "end_stone_bricks" -> Block.BRICKS;
                    case "magenta_stained_glass" -> Block.GLASS;
                    case "obsidian" -> Block.OBSIDIAN;
                    default -> Block.OTHER;
                };
                blocks.put(p, block);
            }
        }
        return blocks;
    }
    private CitySignature.Reader reader(Map<Pos, Block> blocks) { return (x, y, z) -> blocks.getOrDefault(new Pos(x, y, z), Block.OTHER); }
    @Test void recognizesActualCityEntranceAtEveryRotationAndNegativeCoordinates() throws Exception {
        var origin = new Pos(-305, 63, -710);
        for (int r = 0; r < 4; r++) {
            var blocks = fixture("base_floor", origin, r);
            var found = new HashSet<Pos>();
            for (var b : blocks.entrySet()) if (b.getValue() == Block.PILLAR) {
                var city = CitySignature.city(b.getKey(), reader(blocks));
                if (city != null) found.add(city);
            }
            var a = origin.offset(1, 0, 1, r); var b = origin.offset(8, 0, 8, r);
            assertEquals(Set.of(new Pos(Math.floorDiv(a.x() + b.x(), 2), origin.y(), Math.floorDiv(a.z() + b.z(), 2))), found);
        }
    }
    @Test void lootedShipFindsExactFrameCellAtEveryRotation() throws Exception {
        var origin = new Pos(-303, 113, 512);
        for (int r = 0; r < 4; r++) {
            var blocks = fixture("ship", origin, r);
            var frames = new HashSet<Pos>();
            for (var b : blocks.entrySet()) if (b.getValue() == Block.OBSIDIAN) {
                var frame = CitySignature.ship(b.getKey(), reader(blocks));
                if (frame != null) frames.add(frame);
            }
            assertEquals(Set.of(origin.offset(6, 5, 7, r)), frames);
            // A ship is not a city entrance.
            for (var b : blocks.entrySet()) if (b.getValue() == Block.PILLAR)
                assertNull(CitySignature.city(b.getKey(), reader(blocks)));
        }
    }
    @Test void missingOrUnloadedSignatureBlocksCannotDiscoverAStructure() throws Exception {
        var origin = new Pos(0, 80, 0);
        var blocks = fixture("base_floor", origin, 0);
        blocks.put(origin.offset(1, 2, 3, 0), Block.UNLOADED);
        assertNull(CitySignature.city(origin.offset(1, 1, 1, 0), reader(blocks)));
        assertNull(CitySignature.ship(origin, (x, y, z) -> Block.OBSIDIAN));
        assertNull(CitySignature.city(origin, (x, y, z) -> Block.PILLAR));
    }
}
