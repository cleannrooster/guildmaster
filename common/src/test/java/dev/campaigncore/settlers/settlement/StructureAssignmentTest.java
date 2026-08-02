package dev.campaigncore.settlers.settlement;

import dev.campaigncore.settlers.MinecraftTestBase;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureAssignmentTest extends MinecraftTestBase {
    @Test
    void scanBoundsInflatePhysicalBoundsByTwoInEveryDirection() {
        BoundingBox physical = new BoundingBox(10, 20, 30, 14, 25, 36);
        StructureAssignment structure = new StructureAssignment(
                ResourceLocation.fromNamespaceAndPath("settlers", "test"), StructureRole.STORAGE, physical);

        BoundingBox scan = structure.scanBounds();
        assertEquals(8, scan.minX());
        assertEquals(18, scan.minY());
        assertEquals(28, scan.minZ());
        assertEquals(16, scan.maxX());
        assertEquals(27, scan.maxY());
        assertEquals(38, scan.maxZ());
        assertEquals(physical, structure.bounds());
    }

    @Test
    void overlappingStructureScansCannotDuplicateAnchors() {
        AnchorTable anchors = new AnchorTable();
        BlockPos position = new BlockPos(1, 2, 3);
        anchors.add(AnchorType.STORAGE, position);
        anchors.add(AnchorType.STORAGE, position);
        assertEquals(1, anchors.count(AnchorType.STORAGE));
    }
}
