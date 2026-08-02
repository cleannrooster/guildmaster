package dev.campaigncore.washedashore.incident;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.campaigncore.washedashore.encounter.AttributeOverrides;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Optional composition features used by richer hub incidents. */
public record HubIncidentSpecial(Optional<String> locationSlot,Optional<ResourceLocation> protectedEntity,
        int protectedCount,int minimumSurvivors,boolean useNearbyProtected,int waves,int waveIntervalTicks,
        Optional<ResourceLocation> mount,List<ResourceLocation> opponents,int opponentCount,
        AttributeOverrides attributes,AttributeOverrides opponentAttributes) {
    public static final HubIncidentSpecial NONE=new HubIncidentSpecial(Optional.empty(),Optional.empty(),0,0,false,1,100,
            Optional.empty(),List.of(),0,AttributeOverrides.EMPTY,AttributeOverrides.EMPTY);
    public static final Codec<HubIncidentSpecial> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.optionalFieldOf("location_slot").forGetter(HubIncidentSpecial::locationSlot),
            ResourceLocation.CODEC.optionalFieldOf("protected_entity").forGetter(HubIncidentSpecial::protectedEntity),
            Codec.INT.optionalFieldOf("protected_count",0).forGetter(HubIncidentSpecial::protectedCount),
            Codec.INT.optionalFieldOf("minimum_survivors",0).forGetter(HubIncidentSpecial::minimumSurvivors),
            Codec.BOOL.optionalFieldOf("use_nearby_protected",false).forGetter(HubIncidentSpecial::useNearbyProtected),
            Codec.INT.optionalFieldOf("waves",1).forGetter(HubIncidentSpecial::waves),
            Codec.INT.optionalFieldOf("wave_interval_ticks",100).forGetter(HubIncidentSpecial::waveIntervalTicks),
            ResourceLocation.CODEC.optionalFieldOf("mount").forGetter(HubIncidentSpecial::mount),
            ResourceLocation.CODEC.listOf().optionalFieldOf("opponents",List.of()).forGetter(HubIncidentSpecial::opponents),
            Codec.INT.optionalFieldOf("opponent_count",0).forGetter(HubIncidentSpecial::opponentCount),
            AttributeOverrides.CODEC.optionalFieldOf("attributes",AttributeOverrides.EMPTY).forGetter(HubIncidentSpecial::attributes),
            AttributeOverrides.CODEC.optionalFieldOf("opponent_attributes",AttributeOverrides.EMPTY).forGetter(HubIncidentSpecial::opponentAttributes)
    ).apply(i,HubIncidentSpecial::new));
    public HubIncidentSpecial{
        if(protectedCount<0||minimumSurvivors<0||minimumSurvivors>protectedCount||waves<1||waveIntervalTicks<1||opponentCount<0)
            throw new IllegalArgumentException("invalid special incident counts");
        opponents=List.copyOf(opponents);
    }
}
