package dev.campaigncore.campaign;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

public final class EncounterState {
    private String status = "DORMANT";
    private UUID activeBossUuid;
    private BlockPos anchor;
    private BlockPos spawn;
    private boolean oneShot;

    public String status() { return status; }
    public UUID activeBossUuid() { return activeBossUuid; }
    public BlockPos anchor() { return anchor; }
    public BlockPos spawn() { return spawn; }
    public boolean oneShot() { return oneShot; }
    public void restore(String status, UUID boss, BlockPos anchor, BlockPos spawn, boolean oneShot) {
        this.status=status; this.activeBossUuid=boss; this.anchor=anchor; this.spawn=spawn; this.oneShot=oneShot;
    }
    public CompoundTag save() {
        CompoundTag tag=new CompoundTag(); tag.putString("status",status);
        if(activeBossUuid!=null)tag.putUUID("active_boss",activeBossUuid);
        if(anchor!=null)tag.putLong("anchor",anchor.asLong());
        if(spawn!=null)tag.putLong("spawn",spawn.asLong());
        tag.putBoolean("one_shot",oneShot); return tag;
    }
    public static EncounterState load(CompoundTag tag) {
        EncounterState state=new EncounterState();
        state.restore(tag.getString("status"),tag.hasUUID("active_boss")?tag.getUUID("active_boss"):null,
                tag.contains("anchor")?BlockPos.of(tag.getLong("anchor")):null,
                tag.contains("spawn")?BlockPos.of(tag.getLong("spawn")):null,tag.getBoolean("one_shot"));
        return state;
    }
}
