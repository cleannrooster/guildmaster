package dev.campaigncore.settlers.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/// One classified structure inside a settlement: which template it came from (informational only —
/// nothing downstream may branch on it), what settlement role it plays, and its bounds.
public record StructureAssignment(
        ResourceLocation template,
        StructureRole role,
        BoundingBox bounds
) {
    public static final int SCAN_MARGIN = 2;
    public static final Codec<StructureAssignment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("template").forGetter(StructureAssignment::template),
            StructureRole.CODEC.fieldOf("role").forGetter(StructureAssignment::role),
            BoundingBox.CODEC.fieldOf("bounds").forGetter(StructureAssignment::bounds)
    ).apply(instance, StructureAssignment::new));

    public BlockPos center() {
        return this.bounds.getCenter();
    }

    /// Detection boundary used for blocks associated with this structure. Physical placement,
    /// collision, settlement ownership, and center calculations continue using the exact bounds.
    public BoundingBox scanBounds() {
        return inflatedScanBounds(this.bounds);
    }

    public static BoundingBox inflatedScanBounds(BoundingBox bounds) {
        return new BoundingBox(
                bounds.minX() - SCAN_MARGIN, bounds.minY() - SCAN_MARGIN, bounds.minZ() - SCAN_MARGIN,
                bounds.maxX() + SCAN_MARGIN, bounds.maxY() + SCAN_MARGIN, bounds.maxZ() + SCAN_MARGIN);
    }
}
