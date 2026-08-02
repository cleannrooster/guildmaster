package dev.campaigncore.washedashore.mixin;

import dev.campaigncore.washedashore.encounter.EncounterManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the native loot-table drops of an encounter candidate flagged
 * {@code suppress_native_drops} (tagged {@link EncounterManager#SUPPRESS_DROPS_TAG} at spawn), so the
 * campaign can hand out its own rewards instead. Only the mob's loot-table drops are cancelled — this
 * does not touch behavior, XP, or the campaign loot the encounter drops separately on completion.
 */
@Mixin(LivingEntity.class)
public abstract class EncounterDropSuppressionMixin {
    @Inject(method = "dropFromLootTable", at = @At("HEAD"), cancellable = true)
    private void campaignCore$suppressCandidateDrops(DamageSource source, boolean hitByPlayer, CallbackInfo ci) {
        if (((LivingEntity) (Object) this).getTags().contains(EncounterManager.SUPPRESS_DROPS_TAG)) {
            ci.cancel();
        }
    }
}
