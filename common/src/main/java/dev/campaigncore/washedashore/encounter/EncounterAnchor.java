package dev.campaigncore.washedashore.encounter;

import dev.campaigncore.washedashore.act.WashedAshoreStage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public final class EncounterAnchor {
    private final ResourceLocation encounterId;
    private final ResourceLocation bossEntityType;
    private BlockPos anchorPos;
    private BlockPos bossSpawnPos;
    private final int activationRadius;
    private final int resetRadius;
    private final boolean oneShot;
    private final WashedAshoreStage requiredStage;
    private final int retryDelayTicks;
    private EncounterStatus status = EncounterStatus.DORMANT;
    private UUID activeBossUuid;
    /** Boss removed by an unattended failure; retained until its unloaded entity can be discarded. */
    private UUID pendingRemovalBossUuid;
    /** Candidate chosen from the pool for this run (null = not yet selected / use native definition). */
    private ResourceLocation selectedCandidate;
    private int missingChecks;
    /** First game tick at which no player was within the reset radius; 0 while attended. */
    private long noPlayersSince;
    /** Game time at which a FAILED/ABANDONED encounter becomes retryable; 0 when not on cooldown. */
    private long retryAt;

    public EncounterAnchor(ResourceLocation id, ResourceLocation boss, BlockPos anchor, BlockPos spawn,
                           int activationRadius, int resetRadius, boolean oneShot, WashedAshoreStage requiredStage,
                           int retryDelayTicks) {
        this.encounterId=id; this.bossEntityType=boss; this.anchorPos=anchor; this.bossSpawnPos=spawn;
        this.activationRadius=activationRadius; this.resetRadius=resetRadius; this.oneShot=oneShot; this.requiredStage=requiredStage;
        this.retryDelayTicks=retryDelayTicks;
    }
    public ResourceLocation encounterId(){return encounterId;} public ResourceLocation bossEntityType(){return bossEntityType;}
    public BlockPos anchorPos(){return anchorPos;} public BlockPos bossSpawnPos(){return bossSpawnPos;}
    public int activationRadius(){return activationRadius;} public int resetRadius(){return resetRadius;}
    public boolean oneShot(){return oneShot;} public WashedAshoreStage requiredStage(){return requiredStage;}
    public int retryDelayTicks(){return retryDelayTicks;} public long retryAt(){return retryAt;}
    public long noPlayersSince(){return noPlayersSince;}
    public EncounterStatus status(){return status;} public UUID activeBossUuid(){return activeBossUuid;}
    public UUID pendingRemovalBossUuid(){return pendingRemovalBossUuid;}
    public ResourceLocation selectedCandidate(){return selectedCandidate;}
    public void setSelectedCandidate(ResourceLocation candidate){selectedCandidate=candidate;}
    public void relocate(BlockPos anchor, BlockPos spawn){anchorPos=anchor;bossSpawnPos=spawn;}
    public void activate(UUID uuid){status=EncounterStatus.ACTIVE;activeBossUuid=uuid;missingChecks=0;noPlayersSince=0;retryAt=0;}
    public void complete(){status=EncounterStatus.COMPLETED;activeBossUuid=null;missingChecks=0;noPlayersSince=0;retryAt=0;}
    public void reset(){status=EncounterStatus.DORMANT;activeBossUuid=null;selectedCandidate=null;missingChecks=0;noPlayersSince=0;retryAt=0;}
    /** Enters the FAILED cooldown; the encounter becomes retryable after {@link #retryDelayTicks} ticks. */
    public void fail(long now){status=EncounterStatus.FAILED;activeBossUuid=null;missingChecks=0;noPlayersSince=0;retryAt=now+retryDelayTicks;}
    /** Enters the ABANDONED cooldown; the encounter becomes retryable after {@link #retryDelayTicks} ticks. */
    public void abandon(long now){status=EncounterStatus.ABANDONED;activeBossUuid=null;missingChecks=0;noPlayersSince=0;retryAt=now+retryDelayTicks;}
    public boolean awaitingRetry(){return status==EncounterStatus.FAILED||status==EncounterStatus.ABANDONED;}
    public int noteMissing(){return ++missingChecks;} public void found(){missingChecks=0;}
    public boolean noteNoNearbyPlayers(long now){if(noPlayersSince!=0)return false;noPlayersSince=now;return true;}
    public boolean clearNoNearbyPlayers(){if(noPlayersSince==0)return false;noPlayersSince=0;return true;}
    public boolean unattendedFor(long now,long ticks){return noPlayersSince!=0&&now-noPlayersSince>=ticks;}
    public void markBossForRemoval(){pendingRemovalBossUuid=activeBossUuid;}
    public void markBossForRemoval(UUID uuid){pendingRemovalBossUuid=uuid;}
    public void clearPendingRemovalBoss(){pendingRemovalBossUuid=null;}
    public void restore(EncounterStatus value, UUID boss, long retryAt){restore(value,boss,retryAt,0);}
    public void restore(EncounterStatus value, UUID boss, long retryAt,long noPlayersSince){status=value;activeBossUuid=boss;this.retryAt=retryAt;this.noPlayersSince=noPlayersSince;}
    public void restore(EncounterStatus value, UUID boss, long retryAt, ResourceLocation candidate){restore(value,boss,retryAt,0);selectedCandidate=candidate;}
    public void restore(EncounterStatus value, UUID boss, long retryAt, long noPlayersSince, ResourceLocation candidate){restore(value,boss,retryAt,noPlayersSince);selectedCandidate=candidate;}
    public void restorePendingRemovalBoss(UUID value){pendingRemovalBossUuid=value;}
}
