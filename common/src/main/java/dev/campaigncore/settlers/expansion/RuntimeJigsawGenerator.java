package dev.campaigncore.settlers.expansion;

import net.minecraft.server.level.ServerLevel;

public final class RuntimeJigsawGenerator {
    private RuntimeJigsawGenerator() {
    }

    public static RuntimeJigsawResult generate(
            ServerLevel level,
            SettlementExpansionPlan plan
    ) {
        /*
         * Resolve plan.poolId() from Registries.TEMPLATE_POOL.
         *
         * Run the 1.21.1 jigsaw placement operation at plan.origin().
         *
         * Capture the generated PoolElementStructurePiece objects or enough
         * template/bounding-box data to create StructureAssignments.
         *
         * Do not place anything until every proposed piece passes collision
         * validation.
         */
        throw new UnsupportedOperationException(
                "Runtime jigsaw adapter not implemented."
        );
    }
}