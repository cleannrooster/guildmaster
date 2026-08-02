package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.entity.data.SettlerRole;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/// Proactive hostile-mob targeting, gated to the GUARD role. Civilians and workers never initiate
/// combat (they only fight back via HurtByTargetGoal if attacked); only armed personnel patrol for
/// threats. Role is set post-construction via {@link SettlerEntity#applyProfile}, so this goal is
/// always registered and checks the role live rather than being conditionally added at construction.
public final class SettlerAttackTargetGoal extends NearestAttackableTargetGoal<Monster> {
    private final SettlerEntity settler;

    public SettlerAttackTargetGoal(SettlerEntity settler) {
        super(settler, Monster.class, 10, true, false, livingEntity -> !(livingEntity instanceof Creeper));

        this.settler = settler;
    }

    @Override
    public boolean canUse() {
        return this.settler.role() == SettlerRole.GUARD && super.canUse();
    }
}
