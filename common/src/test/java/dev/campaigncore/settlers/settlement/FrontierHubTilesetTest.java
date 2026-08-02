package dev.campaigncore.settlers.settlement;

import com.google.gson.JsonParser;
import dev.campaigncore.settlers.MinecraftTestBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierHubTilesetTest extends MinecraftTestBase {
    @Test
    void allAuthoredTemplatesAreValidCompressedStructureNbt() throws Exception {
        Path directory = resourceDirectory("data/settlers/structure/frontier_hub");
        List<Path> templates;
        try (var files = Files.list(directory)) {
            templates = files.filter(path -> path.toString().endsWith(".nbt")).sorted().toList();
        }
        // Builders may add hand-authored variants without changing this test.
        assertTrue(templates.size() >= 26);
        for (Path template : templates) {
            CompoundTag root = NbtIo.readCompressed(template, NbtAccounter.unlimitedHeap());
            assertEquals(3955, root.getInt("DataVersion"), template.toString());
            assertEquals(3, root.getList("size", Tag.TAG_INT).size(), template.toString());
            assertTrue(!root.getList("palette", Tag.TAG_COMPOUND).isEmpty(), template.toString());
            assertTrue(!root.getList("blocks", Tag.TAG_COMPOUND).isEmpty(), template.toString());
            assertTrue(root.contains("entities", Tag.TAG_LIST), template.toString());
            StructureTemplate parsed = new StructureTemplate();
            parsed.load(BuiltInRegistries.BLOCK.asLookup(), root);
            assertTrue(parsed.getSize().getX() >= 3 && parsed.getSize().getZ() >= 3, template.toString());
        }
    }

    @Test
    void everyTemplateHasParseableAuthoringMetadata() throws Exception {
        Path directory = resourceDirectory("data/settlers/settlement_structures/frontier_hub");
        List<Path> definitions;
        try (var files = Files.list(directory)) {
            definitions = files.filter(path -> path.toString().endsWith(".json")).toList();
        }
        assertEquals(27, definitions.size());
        for (Path definition : definitions) {
            var json = JsonParser.parseString(Files.readString(definition)).getAsJsonObject();
            assertEquals(2, json.get("scan_margin").getAsInt());
            assertEquals(8, json.get("activity_radius").getAsInt());
            assertEquals(3, json.getAsJsonArray("dimensions").size());
            assertTrue(json.has("role"));
        }
    }

    @Test
    void plazaStartsSixPrimaryRoadsAndBranchesCanTerminate() throws Exception {
        Path metadata = resourceDirectory("data/settlers/settlement_structures/frontier_hub").resolve("plaza.json");
        var plaza = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
        var connectors = plaza.getAsJsonArray("connectors");
        assertEquals(6, connectors.size());
        connectors.forEach(connector -> {
            var socket = connector.getAsJsonObject();
            assertEquals("settlers:street", socket.get("target").getAsString());
            assertEquals("settlers:frontier_hub/primary_roads", socket.get("pool").getAsString());
        });

        Path poolFile = resourceDirectory("data/settlers/worldgen/template_pool/frontier_hub").resolve("roads.json");
        var pool = JsonParser.parseString(Files.readString(poolFile)).getAsJsonObject();
        var locations = pool.getAsJsonArray("elements").asList().stream()
                .map(element -> element.getAsJsonObject().getAsJsonObject("element").get("location").getAsString())
                .toList();
        assertTrue(locations.contains("settlers:frontier_hub/road_end"));
        assertTrue(locations.contains("settlers:frontier_hub/small_plaza"));
        assertTrue(locations.stream().noneMatch(location -> location.endsWith("/plaza")
                || location.contains("arrival") || location.contains("t_intersection") || location.contains("cross_intersection")));
    }

    private static Path resourceDirectory(String resource) throws URISyntaxException {
        return Path.of(FrontierHubTilesetTest.class.getClassLoader().getResource(resource).toURI());
    }
}
