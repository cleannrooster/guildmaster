package dev.campaigncore.settlers.expansion;

import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;

import java.util.List;

public record RuntimeJigsawResult(
        boolean success,
        String message,
        List<PoolElementStructurePiece> pieces
) {
    public RuntimeJigsawResult {
        message = message == null ? "" : message;
        pieces = pieces == null
                ? List.of()
                : List.copyOf(pieces);
    }

    public static RuntimeJigsawResult failure(String message) {
        return new RuntimeJigsawResult(
                false,
                message,
                List.of()
        );
    }

    public static RuntimeJigsawResult success(
            List<PoolElementStructurePiece> pieces
    ) {
        return new RuntimeJigsawResult(
                true,
                "Generated jigsaw expansion.",
                pieces
        );
    }
}