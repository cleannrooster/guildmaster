package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.SettlementRecruitmentService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/// An unemployed civilian walks to a reserved workplace before adopting the offered profession.
public final class SeekEmploymentBehavior extends AnchorSeekingBehavior {
    public SeekEmploymentBehavior() {
        super(AnchorType.WORK);
    }

    @Override
    protected Optional<BlockPos> pickTarget(SettlerEntity settler, Settlement settlement) {
        return settler.workAnchor();
    }

    @Override
    protected void onArrive(SettlerEntity settler, Settlement settlement, ServerLevel level) {
        SettlementRecruitmentService.completeJobOffer(level, settlement, settler.getUUID());
    }
}
