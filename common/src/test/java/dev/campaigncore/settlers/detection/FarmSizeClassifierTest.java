package dev.campaigncore.settlers.detection;

import dev.campaigncore.settlers.MinecraftTestBase;
import dev.campaigncore.settlers.settlement.AnchorTable;
import dev.campaigncore.settlers.settlement.AnchorType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmSizeClassifierTest extends MinecraftTestBase {
    @Test
    void farmlandBlocksMapToIntendedAbstractTiles() {
        assertEquals(0, FarmSizeClassifier.farmTier(7, 7));
        assertEquals(1, FarmSizeClassifier.farmTier(8, 8));
        assertEquals(1, FarmSizeClassifier.farmTier(15, 15));
        assertEquals(2, FarmSizeClassifier.farmTier(16, 16));
        assertEquals(2, FarmSizeClassifier.farmTier(31, 31));
        assertEquals(3, FarmSizeClassifier.farmTier(32, 32));
    }

    @Test
    void emptyFarmlandReceivesHalfCredit() {
        assertEquals(0, FarmSizeClassifier.farmTier(15, 0));
        assertEquals(1, FarmSizeClassifier.farmTier(16, 0));
        assertEquals(2, FarmSizeClassifier.farmTier(32, 0));
    }

    @Test
    void eachAbstractTierSupportsSixResidents() {
        assertEquals(6.0, FarmSizeClassifier.foodCapacity(1, 0.0), 1e-9);
        assertEquals(18.0, FarmSizeClassifier.foodCapacity(3, 0.0), 1e-9);
        assertEquals(144.0, FarmSizeClassifier.foodCapacity(24, 0.0), 1e-9);
    }

    @Test
    void eightFishingSitesProvideOneHundredFortyFourFoodCapacity() {
        AnchorTable anchors = new AnchorTable();
        for (int i = 0; i < 8; i++) {
            anchors.add(AnchorType.FISHING, new BlockPos(i * 16, 64, 0));
        }
        int tiers = FarmSizeClassifier.totalFoodTiers(null, List.of(), anchors);
        assertEquals(24, tiers);
        assertEquals(144.0, FarmSizeClassifier.foodCapacity(tiers, 0.0), 1e-9);
    }
}
