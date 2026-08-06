package dev.campaigncore.prestige;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A player's prestige record, keyed by registered campaign/act id so each act levels independently
 * (prestiging Act 1 never touches Act 2's entry). The ledger is the one piece of per-player state
 * that survives a prestige wipe; {@code pendingWipeAct} additionally records which act queued the
 * wipe so a timely logout cannot dodge it — the wipe is applied on next join instead.
 *
 * <p>Stored inside {@link dev.campaigncore.washedashore.act.WashedAshoreProgress} (the primary
 * per-player save entry) but only ever accessed through {@link PrestigeManager}, so future acts
 * never depend on where it physically lives.
 */
public final class PrestigeLedger {
    private final Map<ResourceLocation,Integer> levels=new HashMap<>();
    private ResourceLocation pendingWipeAct;

    public int level(ResourceLocation actId){return levels.getOrDefault(actId,0);}
    public int increment(ResourceLocation actId){int next=level(actId)+1;levels.put(actId,next);return next;}
    public void setLevel(ResourceLocation actId,int level){if(level<=0)levels.remove(actId);else levels.put(actId,level);}
    public Map<ResourceLocation,Integer> levelsView(){return Collections.unmodifiableMap(levels);}
    public ResourceLocation pendingWipeAct(){return pendingWipeAct;}
    public void queueWipe(ResourceLocation actId){pendingWipeAct=actId;}
    public void clearPendingWipe(){pendingWipeAct=null;}

    public CompoundTag save(){
        CompoundTag tag=new CompoundTag();
        CompoundTag acts=new CompoundTag();
        levels.forEach((id,level)->{if(level>0)acts.putInt(id.toString(),level);});
        tag.put("levels",acts);
        if(pendingWipeAct!=null)tag.putString("pending_wipe_act",pendingWipeAct.toString());
        return tag;
    }
    public static PrestigeLedger load(CompoundTag tag){
        PrestigeLedger result=new PrestigeLedger();
        CompoundTag acts=tag.getCompound("levels");
        for(String key:acts.getAllKeys()){
            ResourceLocation id=ResourceLocation.tryParse(key);
            if(id!=null&&acts.getInt(key)>0)result.levels.put(id,acts.getInt(key));
        }
        if(tag.contains("pending_wipe_act"))result.pendingWipeAct=ResourceLocation.tryParse(tag.getString("pending_wipe_act"));
        return result;
    }
}
