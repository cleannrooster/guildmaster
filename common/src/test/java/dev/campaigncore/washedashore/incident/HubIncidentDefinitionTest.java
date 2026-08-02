package dev.campaigncore.washedashore.incident;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.campaigncore.settlers.MinecraftTestBase;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HubIncidentDefinitionTest extends MinecraftTestBase {
    @Test void parsesAllObjectiveAndSpawnStrategies(){
        for(HubIncidentObjectiveType objective:HubIncidentObjectiveType.values())
            for(HubIncidentSpawnType spawn:HubIncidentSpawnType.values()){
                String json="{\"tier\":2,\"objective\":\""+objective.getSerializedName()+"\",\"spawn\":\""+spawn.getSerializedName()+"\",\"entities\":[\"minecraft:zombie\"],\"count\":4}";
                HubIncidentDefinition value=HubIncidentDefinition.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(json)).result().orElseThrow();
                assertEquals(objective,value.objective());assertEquals(spawn,value.spawn());assertEquals(4,value.count());
            }
    }

    @Test void runtimeStateRoundTrips(){
        HubIncidentState state=new HubIncidentState();UUID first=UUID.randomUUID(),leader=UUID.randomUUID();
        state.setNextSelectionAt(40);state.begin(ResourceLocation.parse("campaign_core:test"),100,300,
                new BlockPos(1,64,2),new BlockPos(20,65,30),List.of(first,leader),leader);
        HubIncidentState loaded=HubIncidentState.load(state.save());
        assertEquals(state.activeIncident(),loaded.activeIncident());assertEquals(state.center(),loaded.center());
        assertEquals(state.destination(),loaded.destination());assertEquals(state.members(),loaded.members());assertEquals(leader,loaded.leader());
    }
}
