package dev.campaigncore.washedashore.encounter;

import net.minecraft.resources.ResourceLocation;

/**
 * Data-defined tuning for the Sculk Surface encounter (see
 * {@link dev.campaigncore.washedashore.act.SculkSurfaceManager}). The arena is a swath of ground
 * converted to sculk and dressed with sensors/shriekers; it lies dormant until {@code mobDeathsToTrigger}
 * mobs die within {@code scanRadius}, at which point a Sculken Raven scaled to {@code healthScale} /
 * {@code damageScale} rises, reinforced by up to {@code waveCount} waves of {@code waveSize}
 * {@code waveEntity} spawned {@code waveDelayTicks} apart.
 */
public record SculkArenaProfile(
        double healthScale,
        double damageScale,
        int mobDeathsToTrigger,
        int formationRadius,
        int scanRadius,
        ResourceLocation waveEntity,
        int waveSize,
        int waveCount,
        int waveDelayTicks
){
    public SculkArenaProfile{
        if(healthScale<=0)throw new IllegalArgumentException("sculk health_scale must be positive");
        if(damageScale<0)throw new IllegalArgumentException("sculk damage_scale must not be negative");
        if(mobDeathsToTrigger<1)throw new IllegalArgumentException("sculk mob_deaths_to_trigger must be positive");
        if(formationRadius<=0)throw new IllegalArgumentException("sculk formation_radius must be positive");
        if(scanRadius<=0)throw new IllegalArgumentException("sculk scan_radius must be positive");
        if(waveEntity==null)throw new IllegalArgumentException("sculk wave entity is required");
        if(waveSize<0)throw new IllegalArgumentException("sculk wave_size must not be negative");
        if(waveCount<0)throw new IllegalArgumentException("sculk wave_count must not be negative");
        if(waveDelayTicks<0)throw new IllegalArgumentException("sculk wave_delay_ticks must not be negative");
    }
}
