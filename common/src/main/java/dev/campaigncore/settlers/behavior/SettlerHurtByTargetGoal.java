package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.SettlementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;

/// Vanilla retaliation with a bounded response to hostile players. Settlers defend themselves and
/// their community, but do not chase a player beyond it or remain hostile after a single attack.
public final class SettlerHurtByTargetGoal extends HurtByTargetGoal {
    private final SettlerEntity settler;

    public SettlerHurtByTargetGoal(SettlerEntity settler) {
        super(settler);
        this.settler = settler;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.settler.getTarget();
        if (target instanceof Player player) {
            boolean insideSettlement = isInsideOwningSettlement(player);
            int ticksSinceHostileAction = this.settler.tickCount - this.settler.getLastHurtByMobTimestamp();
            if (!SettlerPlayerHostility.shouldContinue(insideSettlement, ticksSinceHostileAction)) {
                this.settler.setTarget(null);
                return false;
            }
        }
        return super.canContinueToUse();
    }

    private boolean isInsideOwningSettlement(Player player) {
        if (!(this.settler.level() instanceof ServerLevel level)) {
            return false;
        }
        return this.settler.settlementId()
                .flatMap(id -> SettlementManager.get(level).byId(id))
                .map(settlement -> settlement.contains(player.blockPosition()))
                .orElse(false);
    }

}
