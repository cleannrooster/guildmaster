package dev.campaigncore.settlers.economy;

import dev.campaigncore.settlers.production.ProductionState;
import dev.campaigncore.settlers.settlement.AnchorTable;
import dev.campaigncore.settlers.settlement.ResidentEntry;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.SettlementProfile;
import dev.campaigncore.settlers.settlement.StructureAssignment;
import dev.campaigncore.settlers.settlement.ThreatState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.UUID;

/// Shared builders for economy tests: a bare settlement whose mutable production/economy state can be
/// primed directly. No world required.
final class EconomyTestSupport {
    private EconomyTestSupport() {
    }

    static Settlement minimalSettlement() {
        SettlementProfile profile = new SettlementProfile("Ashford", "plains", "agriculture", "",
                "civilian_reeve", "independent_frontier", "hostile_mobs", "none", "frontier_folkways", "stable");
        BoundingBox bounds = new BoundingBox(0, 60, 0, 10, 70, 10);
        List<StructureAssignment> structures = List.of();
        List<ResidentEntry> roster = List.of();
        return new Settlement(UUID.randomUUID(), profile, bounds, new BlockPos(5, 64, 5), structures,
                new AnchorTable(), roster, ThreatState.NORMAL, new ProductionState(), new SettlementEconomyState());
    }
}
