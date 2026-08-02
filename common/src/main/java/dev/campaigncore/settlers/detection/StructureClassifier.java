package dev.campaigncore.settlers.detection;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.mixin.SinglePoolElementAccessor;
import dev.campaigncore.settlers.settlement.StructureAssignment;
import dev.campaigncore.settlers.settlement.StructureRole;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Turns a village {@link StructureStart}'s jigsaw pieces into role-classified
/// {@link StructureAssignment}s. Primary classification is the datapack rule set for the village
/// type; unknown templates fall back to a block-scan heuristic, and failing that become AUXILIARY
/// (logged) — classification must never abort a conversion.
public final class StructureClassifier {
    private StructureClassifier() {
    }

    public static List<StructureAssignment> classify(ServerLevel level, StructureStart start, String villageType) {
        Optional<StructureRoleRules> rules = StructureRoleRules.forVillageType(villageType);
        if (rules.isEmpty()) {
            SettlersMod.LOGGER.warn("No structure_roles rule set for village type '{}'; relying on fallback heuristics only.", villageType);
        }

        List<StructureAssignment> assignments = new ArrayList<>();
        start.getPieces().forEach(piece -> {
            if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
                return;
            }
            ResourceLocation template = templateOf(poolPiece);
            BoundingBox bounds = piece.getBoundingBox();
            StructureRole role = rules.flatMap(ruleSet -> ruleSet.classify(template))
                    .orElseGet(() -> fallbackClassify(level,
                            StructureAssignment.inflatedScanBounds(bounds), template));
            assignments.add(new StructureAssignment(template, role, bounds));
        });
        return assignments;
    }

    private static ResourceLocation templateOf(PoolElementStructurePiece piece) {
        if (piece.getElement() instanceof SinglePoolElement single) {
            Optional<ResourceLocation> location = ((SinglePoolElementAccessor) single)
                    .campaign_core$getTemplate().left();
            if (location.isPresent()) {
                return location.get();
            }
        }
        // Feature pieces (trees, hay bales) and inline templates have no useful id.
        return ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "unknown_piece");
    }

    /// Cheap block-census heuristic for templates no rule matched (e.g. other mods' village additions).
    /// Only runs over loaded chunks; unloaded structures default to AUXILIARY.
    private static StructureRole fallbackClassify(ServerLevel level, BoundingBox bounds, ResourceLocation template) {
        if (!level.hasChunksAt(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ())) {
            SettlersMod.LOGGER.info("Template {} unclassified (chunks not loaded); assigning AUXILIARY.", template);
            return StructureRole.AUXILIARY;
        }

        int beds = 0;
        int workstations = 0;
        int storage = 0;
        int farmland = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (state.getBlock() instanceof BedBlock) {
                        beds++;
                    } else if (state.is(Blocks.SMITHING_TABLE) || state.is(Blocks.ANVIL) || state.is(Blocks.FLETCHING_TABLE)
                            || state.is(Blocks.STONECUTTER) || state.is(Blocks.LOOM) || state.is(Blocks.GRINDSTONE)
                            || state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)) {
                        workstations++;
                    } else if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) {
                        storage++;
                    } else if (state.is(Blocks.FARMLAND)) {
                        farmland++;
                    }
                }
            }
        }

        StructureRole role;
        if (farmland >= 8) {
            role = StructureRole.FARM;
        } else if (beds > 0) {
            role = StructureRole.RESIDENCE;
        } else if (workstations > 0) {
            role = StructureRole.WORKSHOP;
        } else if (storage >= 2) {
            role = StructureRole.STORAGE;
        } else {
            role = StructureRole.AUXILIARY;
        }
        SettlersMod.LOGGER.info("Template {} classified by fallback heuristic as {} (beds={}, workstations={}, storage={}, farmland={}).",
                template, role, beds, workstations, storage, farmland);
        return role;
    }
}
