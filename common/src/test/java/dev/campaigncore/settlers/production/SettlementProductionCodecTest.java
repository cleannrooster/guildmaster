package dev.campaigncore.settlers.production;

import dev.campaigncore.settlers.MinecraftTestBase;
import dev.campaigncore.settlers.economy.SettlementEconomyState;
import dev.campaigncore.settlers.settlement.AnchorTable;
import dev.campaigncore.settlers.settlement.ResidentEntry;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.SettlementProfile;
import dev.campaigncore.settlers.settlement.StructureAssignment;
import dev.campaigncore.settlers.settlement.ThreatState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SettlementProductionCodecTest extends MinecraftTestBase {
    @Test
    void productionStateRoundTrips() {
        ProductionState state = new ProductionState();
        state.addStored(SettlementResources.FOOD, 12.5);
        state.addStored(SettlementResources.FISH, 3.0);
        state.addProgress(ProductionProcesses.all().get(0).id(), 0.6);
        state.setLastProcessedGameTime(4242L);

        Tag encoded = ProductionState.CODEC.encodeStart(NbtOps.INSTANCE, state).result().orElseThrow();
        ProductionState loaded = ProductionState.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(12.5, loaded.stored(SettlementResources.FOOD), 1e-9);
        assertEquals(3.0, loaded.stored(SettlementResources.FISH), 1e-9);
        assertEquals(0.6, loaded.progress(ProductionProcesses.all().get(0).id()), 1e-9);
        assertEquals(4242L, loaded.lastProcessedGameTime());
    }

    @Test
    void oldSettlementWithoutProductionLoadsWithOwnedDefault() {
        Settlement settlement = minimalSettlement();
        CompoundTag tag = (CompoundTag) Settlement.CODEC.encodeStart(NbtOps.INSTANCE, settlement).result().orElseThrow();
        // Simulate a save that predates the production field.
        tag.remove("production");

        Settlement first = Settlement.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
        Settlement second = Settlement.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();

        assertNotNull(first.production());
        assertEquals(0.0, first.production().stored(SettlementResources.FOOD), 1e-9);

        // Each settlement must own its own state, not share the EMPTY sentinel — mutating one must not
        // leak into the other or into the shared default.
        assertNotSame(first.production(), second.production());
        assertNotSame(first.production(), ProductionState.EMPTY);
        first.production().addStored(SettlementResources.FOOD, 99.0);
        assertEquals(0.0, second.production().stored(SettlementResources.FOOD), 1e-9);
        assertEquals(0.0, ProductionState.EMPTY.stored(SettlementResources.FOOD), 1e-9);
    }

    private static Settlement minimalSettlement() {
        SettlementProfile profile = new SettlementProfile("Test", "plains", "agriculture", "",
                "civilian_reeve", "independent_frontier", "hostile_mobs", "none", "frontier_folkways", "stable");
        BoundingBox bounds = new BoundingBox(0, 60, 0, 10, 70, 10);
        BlockPos center = new BlockPos(5, 64, 5);
        List<StructureAssignment> structures = List.of();
        List<ResidentEntry> roster = List.of();
        return new Settlement(UUID.randomUUID(), profile, bounds, center, structures,
                new AnchorTable(), roster, ThreatState.NORMAL, new ProductionState(), new SettlementEconomyState());
    }
}
