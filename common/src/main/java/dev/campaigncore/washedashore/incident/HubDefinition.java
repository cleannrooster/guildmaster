package dev.campaigncore.washedashore.incident;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import dev.campaigncore.washedashore.act.WashedAshoreInstance;
import java.util.List;

/** Data-defined scheduling policy for one of the authored campaign hub layout slots. */
public record HubDefinition(int tier,String slot,int intervalTicks,int playerRadius,List<ResourceLocation> incidents) {
    public static final Codec<HubDefinition> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.INT.fieldOf("tier").forGetter(HubDefinition::tier),
            Codec.STRING.fieldOf("slot").forGetter(HubDefinition::slot),
            Codec.INT.optionalFieldOf("interval_ticks",30*60*20).forGetter(HubDefinition::intervalTicks),
            Codec.INT.optionalFieldOf("player_radius",192).forGetter(HubDefinition::playerRadius),
            ResourceLocation.CODEC.listOf().optionalFieldOf("incidents",List.of()).forGetter(HubDefinition::incidents)
    ).apply(i,HubDefinition::new));
    public HubDefinition{
        if(tier<1)throw new IllegalArgumentException("hub tier must be positive");
        if(slot==null||slot.isBlank())throw new IllegalArgumentException("hub slot is required");
        if(!WashedAshoreInstance.SLOTS.contains(slot))throw new IllegalArgumentException("unknown hub layout slot: "+slot);
        if(intervalTicks<20||playerRadius<1)throw new IllegalArgumentException("hub timing/radius is invalid");
        incidents=List.copyOf(incidents);
    }
}
