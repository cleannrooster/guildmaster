package dev.campaigncore.settlers.economy;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.StructureAssignment;
import dev.campaigncore.settlers.settlement.StructureRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashSet;
import java.util.Set;

/// A finite food reserve represented by physical blocks in settlers:food_storage_block.
public final class SettlementFoodStorageReserve {
    public static final double FOOD_PER_STORAGE_BLOCK = 1.0;
    public static final TagKey<Block> FOOD_STORAGE_BLOCK = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "food_storage_block"));

    private SettlementFoodStorageReserve() {
    }

    public static int count(ServerLevel level, Settlement settlement) {
        return visit(level, settlement, 0, false);
    }

    /// Removes up to {@code requested} tagged storage blocks and returns the number removed.
    public static int consume(ServerLevel level, Settlement settlement, int requested) {
        return requested <= 0 ? 0 : visit(level, settlement, requested, true);
    }

    private static int visit(ServerLevel level, Settlement settlement, int limit, boolean remove) {
        Set<BlockPos> visited = new HashSet<>();
        int found = 0;
        for (StructureAssignment structure : settlement.structures()) {
            if (structure.role() == StructureRole.ROAD) {
                continue;
            }
            BoundingBox bounds = structure.scanBounds();
            if (!level.hasChunksAt(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ())) {
                continue;
            }
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        BlockPos pos = cursor.set(x, y, z).immutable();
                        if (!visited.add(pos) || !level.getBlockState(pos).is(FOOD_STORAGE_BLOCK)) {
                            continue;
                        }
                        found++;
                        if (remove) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                            if (found >= limit) {
                                return found;
                            }
                        }
                    }
                }
            }
        }
        return found;
    }
}
