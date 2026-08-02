package dev.campaigncore.settlers.detection;

import dev.campaigncore.settlers.settlement.AnchorTable;
import dev.campaigncore.settlers.settlement.AnchorType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/// Counts anchors of a given type for roster-size scaling, capped per chunk so a dense cluster of
/// workstations in one small area — a pathological modded structure, overlapping jigsaw pieces, or
/// just an unusually tight build — can't run away the roster size. A settlement spread across many
/// chunks still scales up normally; each chunk just contributes at most its own cap toward the total,
/// regardless of how many anchors of that type happen to sit inside it.
public final class WorkstationCounter {
    private static final int MAX_PER_CHUNK = 8;

    private WorkstationCounter() {
    }

    public static int effectiveCount(AnchorTable anchors, AnchorType type) {
        Map<Long, Integer> perChunk = new HashMap<>();
        for (BlockPos pos : anchors.all(type)) {
            long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            perChunk.merge(chunkKey, 1, Integer::sum);
        }
        int total = 0;
        for (int count : perChunk.values()) {
            total += Math.min(count, MAX_PER_CHUNK);
        }
        return total;
    }
}
