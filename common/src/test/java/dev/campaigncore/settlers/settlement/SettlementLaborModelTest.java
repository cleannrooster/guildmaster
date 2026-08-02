package dev.campaigncore.settlers.settlement;

import dev.campaigncore.settlers.MinecraftTestBase;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Locks the labor-demand ratios to the values the roster generator has always used, so the shared
/// model and the converter can never silently diverge.
class SettlementLaborModelTest extends MinecraftTestBase {
    /// Adds {@code count} anchors of a type, each in its own chunk so the per-chunk cap
    /// ({@link dev.campaigncore.settlers.detection.WorkstationCounter}) never trims the effective count.
    private static AnchorTable spread(AnchorType type, int count) {
        AnchorTable anchors = new AnchorTable();
        for (int i = 0; i < count; i++) {
            anchors.add(type, new BlockPos(i * 16, 64, 0));
        }
        return anchors;
    }

    @Test
    void farmerRatioIsTwoPerAnchorCappedAtSix() {
        assertEquals(0, SettlementLaborModel.requiredWorkers(new AnchorTable(), "farmer"));
        assertEquals(1, SettlementLaborModel.requiredWorkers(spread(AnchorType.CROP_TENDING, 1), "farmer"));
        assertEquals(2, SettlementLaborModel.requiredWorkers(spread(AnchorType.CROP_TENDING, 4), "farmer"));
        assertEquals(6, SettlementLaborModel.requiredWorkers(spread(AnchorType.CROP_TENDING, 20), "farmer"));
    }

    @Test
    void herderRatioIsOnePerAnchorCappedAtEight() {
        assertEquals(4, SettlementLaborModel.requiredWorkers(spread(AnchorType.ANIMAL_TENDING, 4), "herder"));
        assertEquals(8, SettlementLaborModel.requiredWorkers(spread(AnchorType.ANIMAL_TENDING, 12), "herder"));
    }

    @Test
    void fisherRatioIsOnePerAnchorCappedAtEight() {
        assertEquals(3, SettlementLaborModel.requiredWorkers(spread(AnchorType.FISHING, 3), "fisher"));
        assertEquals(8, SettlementLaborModel.requiredWorkers(spread(AnchorType.FISHING, 12), "fisher"));
    }

    @Test
    void shrineKeeperRatioIsFourPerAnchorCappedAtTwo() {
        assertEquals(1, SettlementLaborModel.requiredWorkers(spread(AnchorType.SHRINE, 4), "shrine_keeper"));
        assertEquals(2, SettlementLaborModel.requiredWorkers(spread(AnchorType.SHRINE, 8), "shrine_keeper"));
    }

    @Test
    void smithRatioIsThreePerAnchorCappedAtFour() {
        assertEquals(1, SettlementLaborModel.requiredWorkers(spread(AnchorType.WORK, 3), "smith"));
        assertEquals(4, SettlementLaborModel.requiredWorkers(spread(AnchorType.WORK, 12), "smith"));
    }

    @Test
    void unknownProfessionHasNoDemand() {
        assertEquals(0, SettlementLaborModel.requiredWorkers(spread(AnchorType.CROP_TENDING, 10), "civilian"));
    }

    @Test
    void perChunkCapLimitsEffectiveCount() {
        // Many anchors crammed into a single chunk are capped by WorkstationCounter, so they demand
        // strictly fewer farmers than the same number of anchors spread one-per-chunk (which saturates
        // the farmer cap). Asserted as a comparison so it stays correct regardless of the exact
        // per-chunk cap value.
        AnchorTable dense = new AnchorTable();
        // 16 distinct positions all inside chunk (0,0): x and z stay within 0..15.
        for (int i = 0; i < 16; i++) {
            dense.add(AnchorType.CROP_TENDING, new BlockPos(i, 64, 0));
        }
        int denseFarmers = SettlementLaborModel.requiredWorkers(dense, "farmer");
        int spreadFarmers = SettlementLaborModel.requiredWorkers(spread(AnchorType.CROP_TENDING, 16), "farmer");
        assertTrue(denseFarmers < spreadFarmers,
                "dense cluster (" + denseFarmers + ") must be capped below spread (" + spreadFarmers + ")");
    }
}
