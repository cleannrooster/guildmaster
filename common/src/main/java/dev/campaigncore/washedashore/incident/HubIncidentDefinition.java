package dev.campaigncore.washedashore.incident;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Reusable objective plus spawn policy. The datapack resource id is the incident id. */
public record HubIncidentDefinition(int tier,int weight,int durationTicks,HubIncidentObjectiveType objective,
                                    HubIncidentSpawnType spawn,List<ResourceLocation> entities,int count,
                                    int minimumDistance,int maximumDistance,int defenseRadius,HubIncidentSpecial special) {
    public static final Codec<HubIncidentDefinition> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.INT.fieldOf("tier").forGetter(HubIncidentDefinition::tier),
            Codec.INT.optionalFieldOf("weight",1).forGetter(HubIncidentDefinition::weight),
            Codec.INT.optionalFieldOf("duration_ticks",10*60*20).forGetter(HubIncidentDefinition::durationTicks),
            HubIncidentObjectiveType.CODEC.fieldOf("objective").forGetter(HubIncidentDefinition::objective),
            HubIncidentSpawnType.CODEC.fieldOf("spawn").forGetter(HubIncidentDefinition::spawn),
            ResourceLocation.CODEC.listOf().fieldOf("entities").forGetter(HubIncidentDefinition::entities),
            Codec.INT.optionalFieldOf("count",1).forGetter(HubIncidentDefinition::count),
            Codec.INT.optionalFieldOf("minimum_distance",40).forGetter(HubIncidentDefinition::minimumDistance),
            Codec.INT.optionalFieldOf("maximum_distance",80).forGetter(HubIncidentDefinition::maximumDistance),
            Codec.INT.optionalFieldOf("defense_radius",12).forGetter(HubIncidentDefinition::defenseRadius),
            HubIncidentSpecial.CODEC.optionalFieldOf("special",HubIncidentSpecial.NONE).forGetter(HubIncidentDefinition::special)
    ).apply(i,HubIncidentDefinition::new));
    public HubIncidentDefinition{
        if(tier<1||weight<1||durationTicks<20||count<1)throw new IllegalArgumentException("incident numeric values must be positive");
        if(entities.isEmpty())throw new IllegalArgumentException("incident entities are required");
        if(minimumDistance<1||maximumDistance<minimumDistance||defenseRadius<1)throw new IllegalArgumentException("incident distances are invalid");
        entities=List.copyOf(entities);
        if(special==null)special=HubIncidentSpecial.NONE;
    }
}
