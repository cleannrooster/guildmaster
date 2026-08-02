package dev.campaigncore.settlers.economy;

import dev.campaigncore.settlers.MinecraftTestBase;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class SettlementEconomyCodecTest extends MinecraftTestBase {
    @Test
    void oldSettlementWithoutEconomyLoadsWithOwnedDefault() {
        Settlement settlement = EconomyTestSupport.minimalSettlement();
        CompoundTag tag = (CompoundTag) Settlement.CODEC.encodeStart(NbtOps.INSTANCE, settlement).result().orElseThrow();
        // Simulate a save that predates the economy field.
        tag.remove("economy");

        Settlement first = Settlement.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
        Settlement second = Settlement.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();

        assertNotNull(first.economy());
        assertEquals(0.0, first.economy().foodDebt(), 1e-9);
        assertEquals(0, first.economy().claimableProvisionBundles());

        // Each settlement owns its own economy; the shared EMPTY sentinel must never be mutated.
        assertNotSame(first.economy(), second.economy());
        assertNotSame(first.economy(), SettlementEconomyState.EMPTY);
        first.economy().addBundles(4);
        assertEquals(0, second.economy().claimableProvisionBundles());
        assertEquals(0, SettlementEconomyState.EMPTY.claimableProvisionBundles());
    }
}
