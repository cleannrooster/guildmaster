package dev.campaigncore.settlers.settlement;

import net.minecraft.core.BlockPos;

public record RecruitmentResult(boolean success, String reason, ResidentEntry resident,
                                BlockPos home, BlockPos work) {
    public static RecruitmentResult failure(String reason) {
        return new RecruitmentResult(false, reason, null, null, null);
    }

    public static RecruitmentResult success(ResidentEntry resident, BlockPos home, BlockPos work) {
        return new RecruitmentResult(true, "", resident, home, work);
    }
}
