package dev.campaigncore.washedashore.incident;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Persistent scheduler/runtime state for one hub. */
public final class HubIncidentState {
    private long nextSelectionAt;
    private ResourceLocation activeIncident,lastIncident;
    private long startedAt,expiresAt;
    private BlockPos center,destination;
    private final Set<UUID> members=new LinkedHashSet<>();
    private final Set<UUID> protectedEntities=new LinkedHashSet<>(),temporaryEntities=new LinkedHashSet<>(),opponents=new LinkedHashSet<>();
    private UUID leader;
    private int wave;
    private long nextWaveAt;

    public long nextSelectionAt(){return nextSelectionAt;} public void setNextSelectionAt(long value){nextSelectionAt=value;}
    public ResourceLocation activeIncident(){return activeIncident;} public ResourceLocation lastIncident(){return lastIncident;}
    public long startedAt(){return startedAt;} public long expiresAt(){return expiresAt;}
    public BlockPos center(){return center;} public BlockPos destination(){return destination;}
    public Set<UUID> members(){return members;} public UUID leader(){return leader;}
    public Set<UUID> protectedEntities(){return protectedEntities;} public Set<UUID> temporaryEntities(){return temporaryEntities;}
    public Set<UUID> opponents(){return opponents;} public int wave(){return wave;} public long nextWaveAt(){return nextWaveAt;}
    public boolean active(){return activeIncident!=null;}
    public void begin(ResourceLocation id,long now,long expires,BlockPos center,BlockPos destination,Collection<UUID> members,UUID leader){
        activeIncident=id;startedAt=now;expiresAt=expires;this.center=center;this.destination=destination;
        this.members.clear();this.members.addAll(members);this.leader=leader;wave=1;nextWaveAt=0;
    }
    public void addMembers(Collection<UUID> values){members.addAll(values);wave++;nextWaveAt=0;}
    public void setNextWaveAt(long value){nextWaveAt=value;}
    public void finish(long next){lastIncident=activeIncident;activeIncident=null;startedAt=expiresAt=nextWaveAt=0;destination=null;members.clear();protectedEntities.clear();temporaryEntities.clear();opponents.clear();leader=null;wave=0;nextSelectionAt=next;}

    public CompoundTag save(){
        CompoundTag tag=new CompoundTag();tag.putLong("next",nextSelectionAt);
        if(activeIncident!=null)tag.putString("active",activeIncident.toString());
        if(lastIncident!=null)tag.putString("last",lastIncident.toString());
        if(startedAt>0)tag.putLong("started",startedAt);if(expiresAt>0)tag.putLong("expires",expiresAt);
        if(center!=null)tag.putLong("center",center.asLong());if(destination!=null)tag.putLong("destination",destination.asLong());
        putIds(tag,"members",members);putIds(tag,"protected",protectedEntities);putIds(tag,"temporary",temporaryEntities);putIds(tag,"opponents",opponents);
        if(leader!=null)tag.putUUID("leader",leader);tag.putInt("wave",wave);if(nextWaveAt>0)tag.putLong("next_wave",nextWaveAt);return tag;
    }
    public static HubIncidentState load(CompoundTag tag){
        HubIncidentState state=new HubIncidentState();state.nextSelectionAt=tag.getLong("next");
        state.activeIncident=ResourceLocation.tryParse(tag.getString("active"));state.lastIncident=ResourceLocation.tryParse(tag.getString("last"));
        state.startedAt=tag.getLong("started");state.expiresAt=tag.getLong("expires");
        if(tag.contains("center",Tag.TAG_LONG))state.center=BlockPos.of(tag.getLong("center"));
        if(tag.contains("destination",Tag.TAG_LONG))state.destination=BlockPos.of(tag.getLong("destination"));
        readIds(tag,"members",state.members);readIds(tag,"protected",state.protectedEntities);readIds(tag,"temporary",state.temporaryEntities);readIds(tag,"opponents",state.opponents);
        if(tag.hasUUID("leader"))state.leader=tag.getUUID("leader");state.wave=tag.getInt("wave");state.nextWaveAt=tag.getLong("next_wave");return state;
    }
    private static void putIds(CompoundTag tag,String key,Collection<UUID> ids){ListTag list=new ListTag();for(UUID id:ids){CompoundTag value=new CompoundTag();value.putUUID("id",id);list.add(value);}tag.put(key,list);}
    private static void readIds(CompoundTag tag,String key,Collection<UUID> out){ListTag list=tag.getList(key,Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++){CompoundTag value=list.getCompound(i);if(value.hasUUID("id"))out.add(value.getUUID("id"));}}
}
