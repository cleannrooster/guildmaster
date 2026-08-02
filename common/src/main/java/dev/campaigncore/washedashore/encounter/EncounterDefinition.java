package dev.campaigncore.washedashore.encounter;

import dev.campaigncore.washedashore.act.WashedAshoreStage;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/**
 * Data-defined spawn table entry for a single Act encounter. Positions are not
 * stored here: the {@code anchor} names a layout slot the act resolves at
 * runtime (see EncounterManager#registerDefaults), optionally shifted by
 * {@code anchorOffset}. {@code activation} is the optional buildup meter that
 * gates the encounter (null for encounters that spawn on plain proximity).
 */
public record EncounterDefinition(
        ResourceLocation id,
        ResourceLocation bossEntity,
        String anchor,
        Vec3i anchorOffset,
        int activationRadius,
        int resetRadius,
        boolean oneShot,
        WashedAshoreStage requiredStage,
        boolean placeholder,
        int retryDelayTicks,
        EncounterCompletion onComplete,
        ActivationProfile activation,
        List<RaidMember> raid,
        HordeProfile horde,
        SculkArenaProfile sculk
) {
    /** Default cooldown before a failed/abandoned encounter becomes retryable: 10 minutes. */
    public static final int DEFAULT_RETRY_DELAY_TICKS=10*60*20;
    /** One line of a multi-mob raid roster: {@code count} copies of {@code entity} filling {@code role}. */
    public record RaidMember(ResourceLocation entity,int count,RaidRole role){
        public static final com.mojang.serialization.Codec<RaidMember> CODEC=
                com.mojang.serialization.codecs.RecordCodecBuilder.create(instance->instance.group(
                        ResourceLocation.CODEC.fieldOf("entity").forGetter(RaidMember::entity),
                        com.mojang.serialization.Codec.INT.optionalFieldOf("count",1).forGetter(RaidMember::count),
                        RaidRole.CODEC.optionalFieldOf("role",RaidRole.FRONTLINE).forGetter(RaidMember::role)
                ).apply(instance,RaidMember::new));
        /** Convenience: a frontline member (used by the legacy Gson encounter loader). */
        public RaidMember(ResourceLocation entity,int count){this(entity,count,RaidRole.FRONTLINE);}
        public RaidMember{
            if(entity==null)throw new IllegalArgumentException("raid member entity is required");
            if(count<1)throw new IllegalArgumentException("raid member count must be positive");
            if(role==null)role=RaidRole.FRONTLINE;
        }
    }
    /** True when this encounter is a scripted multi-mob raid rather than a single boss. */
    public boolean isRaid(){return raid!=null&&!raid.isEmpty();}
    /** True when this encounter rises as a wave horde rather than a single boss. */
    public boolean isHorde(){return horde!=null;}
    /** True when this encounter is the death-triggered Sculk Surface arena. */
    public boolean isSculkArena(){return sculk!=null;}
    public EncounterDefinition {
        if(id==null)throw new IllegalArgumentException("encounter id is required");
        if(bossEntity==null)throw new IllegalArgumentException("boss_entity is required");
        if(anchor==null||anchor.isBlank())throw new IllegalArgumentException("anchor is required");
        if(requiredStage==null)throw new IllegalArgumentException("required_stage is required");
        if(activationRadius<=0||resetRadius<=0)throw new IllegalArgumentException("activation_radius and reset_radius must be positive");
        if(retryDelayTicks<0)throw new IllegalArgumentException("retry_delay_ticks must not be negative");
        if(anchorOffset==null)anchorOffset=Vec3i.ZERO;
        if(onComplete==null)onComplete=EncounterCompletion.DEFAULT;
        raid=raid==null?List.of():List.copyOf(raid);
    }
}
