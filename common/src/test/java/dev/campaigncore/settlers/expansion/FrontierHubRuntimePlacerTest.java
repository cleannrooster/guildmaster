package dev.campaigncore.settlers.expansion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FrontierHubRuntimePlacerTest {
    @Test
    void structureFootprintUsesStructureHeight() {
        assertEquals(70, FrontierHubRuntimePlacer.blendHeight(60, 70, 0.0, 10));
    }

    @Test
    void blendReachesExistingTerrainAtOuterEdge() {
        assertEquals(60, FrontierHubRuntimePlacer.blendHeight(60, 70, 10.0, 10));
    }

    @Test
    void blendInterpolatesBetweenStructureAndExistingTerrain() {
        assertEquals(65, FrontierHubRuntimePlacer.blendHeight(60, 70, 5.0, 10));
    }

    @Test
    void ruinedLayoutKeepsApproximatelyHalfTheGeneratedPieces() {
        assertEquals(1, FrontierHubRuntimePlacer.ruinedPieceCount(1));
        assertEquals(4, FrontierHubRuntimePlacer.ruinedPieceCount(8));
        assertEquals(5, FrontierHubRuntimePlacer.ruinedPieceCount(9));
    }

    @Test
    void ruinWeatheringIsDeterministicAndBounded() {
        int first = FrontierHubRuntimePlacer.ruinRoll(1234L, 10, 64, -20, 17);
        int second = FrontierHubRuntimePlacer.ruinRoll(1234L, 10, 64, -20, 17);
        assertEquals(first, second);
        assertTrue(first >= 0 && first < 100);
    }
}
