package dev.campaigncore.washedashore.recovery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

public final class ProneRecoveryData {
    private ProneRecoveryState state=ProneRecoveryState.INACTIVE;
    private ProneRecoveryReason reason=ProneRecoveryReason.SCRIPTED;
    private boolean firstAwakeningComplete;
    private int accumulatedMovementTicks,requiredMovementTicks,obstructionGraceTicks,protectionTicks;
    private boolean deathRecovery;
    private Vec3 previousPosition;

    public ProneRecoveryState state(){return state;} public ProneRecoveryReason reason(){return reason;}
    public boolean firstAwakeningComplete(){return firstAwakeningComplete;}
    public int accumulatedMovementTicks(){return accumulatedMovementTicks;} public int requiredMovementTicks(){return requiredMovementTicks;}
    public int obstructionGraceTicks(){return obstructionGraceTicks;} public int protectionTicks(){return protectionTicks;}
    public boolean deathRecovery(){return deathRecovery;} public Vec3 previousPosition(){return previousPosition;}
    public boolean active(){return state==ProneRecoveryState.WAITING_FOR_MOVEMENT||state==ProneRecoveryState.RECOVERING;}
    public void begin(ProneRecoveryReason reason,int ticks,Vec3 position,int protection){
        this.state=ProneRecoveryState.WAITING_FOR_MOVEMENT;this.reason=reason;this.requiredMovementTicks=Math.max(1,ticks);
        accumulatedMovementTicks=0;obstructionGraceTicks=0;previousPosition=position;deathRecovery=reason==ProneRecoveryReason.DEATH_RESPAWN;
        protectionTicks=Math.max(0,protection);
    }
    public void setPreviousPosition(Vec3 value){previousPosition=value;} public void setState(ProneRecoveryState value){state=value;}
    public void increment(){accumulatedMovementTicks++;} public void setGrace(int value){obstructionGraceTicks=Math.max(0,value);}
    public void tickGrace(){if(obstructionGraceTicks>0)obstructionGraceTicks--;} public void tickProtection(){if(protectionTicks>0)protectionTicks--;}
    public void endProtection(){protectionTicks=0;}
    public void complete(){state=ProneRecoveryState.COMPLETE;accumulatedMovementTicks=requiredMovementTicks;if(reason==ProneRecoveryReason.FIRST_AWAKENING)firstAwakeningComplete=true;deathRecovery=false;protectionTicks=0;obstructionGraceTicks=0;}
    public void cancel(){state=ProneRecoveryState.INACTIVE;deathRecovery=false;protectionTicks=0;obstructionGraceTicks=0;}
    public void resetFirstAwakening(){firstAwakeningComplete=false;}

    public CompoundTag save(){
        CompoundTag tag=new CompoundTag();tag.putString("state",state.name());tag.putString("reason",reason.name());
        tag.putBoolean("first_complete",firstAwakeningComplete);tag.putInt("movement",accumulatedMovementTicks);
        tag.putInt("required",requiredMovementTicks);tag.putInt("grace",obstructionGraceTicks);tag.putInt("protection",protectionTicks);
        tag.putBoolean("death",deathRecovery);return tag;
    }
    public static ProneRecoveryData load(CompoundTag tag){
        ProneRecoveryData d=new ProneRecoveryData();
        try{d.state=ProneRecoveryState.valueOf(tag.getString("state"));}catch(Exception ignored){}
        try{d.reason=ProneRecoveryReason.valueOf(tag.getString("reason"));}catch(Exception ignored){}
        d.firstAwakeningComplete=tag.getBoolean("first_complete");d.accumulatedMovementTicks=Math.max(0,tag.getInt("movement"));
        d.requiredMovementTicks=Math.max(0,tag.getInt("required"));d.obstructionGraceTicks=Math.max(0,tag.getInt("grace"));
        d.protectionTicks=Math.max(0,tag.getInt("protection"));d.deathRecovery=tag.getBoolean("death");
        if(d.active()&&(d.requiredMovementTicks<=0||d.accumulatedMovementTicks>d.requiredMovementTicks))d.cancel();
        return d;
    }
}
