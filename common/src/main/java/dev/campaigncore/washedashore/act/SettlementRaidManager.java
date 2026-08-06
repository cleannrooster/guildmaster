package dev.campaigncore.washedashore.act;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.config.CampaignServerConfig;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import dev.campaigncore.washedashore.encounter.EncounterAnchor;
import dev.campaigncore.washedashore.encounter.EncounterDefinition;
import dev.campaigncore.washedashore.encounter.EncounterManager;
import dev.campaigncore.washedashore.encounter.EncounterStatus;
import dev.campaigncore.washedashore.encounter.CampaignSpawnProtection;
import dev.campaigncore.washedashore.message.CampaignMessages;
import dev.campaigncore.washedashore.message.SettlementDialogueNames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The third regional encounter: once a player visits the distant settlement (the second town, not
 * Devil's Crossing), it is raided by the roster defined on the {@code regional_boss_c} encounter.
 * The raiders are tagged so {@code SettlementRaidDamageMixin} can soften settler damage against them,
 * and the encounter finishes and succeeds once every raider is dead or gone from the town.
 */
public final class SettlementRaidManager {
    /** Marks a mob as part of the settlement raid (read by the damage mixin and the clear-check). */
    public static final String RAID_MEMBER_TAG="campaign_core_washed_ashore_settlement_raid";
    private static final String RAID_INSTANCE_TAG=RAID_MEMBER_TAG+"_instance=";
    /** One-time flag: the raid has already been triggered for this world. */
    private static final ResourceLocation RAID_STARTED=CampaignCore.washedAshoreId("settlement_raid_started");
    /** Set when the roster could not spawn (sister mod absent): suppresses a false auto-completion. */
    private static final ResourceLocation RAID_UNAVAILABLE=CampaignCore.washedAshoreId("settlement_raid_unavailable");
    /** Horizontal radius (blocks) around the town used to scatter raiders and to scan for survivors. */
    private static final int RAID_SCAN_RADIUS=96;
    /** Five real-time minutes at the normal 20 server ticks per second. */
    private static final long MERCY_COMPLETION_DELAY_TICKS=5L*60L*20L;
    private static final String RPG_MINIBOSSES_MOD="rpg-minibosses";
    private static final ResourceLocation RPG_MINIBOSSES_RAID=ResourceLocation.fromNamespaceAndPath(
            "campaign_core","settlement_raid/native_rpg_minibosses");

    private SettlementRaidManager(){}

    /** Trigger handler: spawn the raid the first time a player reaches the built second settlement. */
    public static void onOtherSettlementArrival(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player){
        WashedAshoreInstance act=data.instances().stream().filter(WashedAshoreInstance::contentReady)
                .filter(i->i.otherSettlement()!=null)
                .min(java.util.Comparator.comparingDouble(i->player.blockPosition().distSqr(i.otherSettlement()))).orElse(data.act());
        onOtherSettlementArrival(level,data,player,act);
    }

    /** Exact-owner variant used by location triggers after they resolve the nearest reinstance. */
    public static void onOtherSettlementArrival(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player,WashedAshoreInstance act){
        if(!act.contentReady())return;
        WashedAshoreProgress progress=data.player(player.getUUID());
        if(progress.crossingQuest()!=RegionalQuestStage.COMPLETE
                ||!progress.defeatedBosses().contains(EncounterManager.THRASHER))return;
        if(act.completedWorldObjectives().contains(RAID_STARTED)
                ||act.completedWorldObjectives().contains(EncounterManager.REGIONAL_C))return;
        EncounterAnchor encounter=act.encounters().get(EncounterManager.REGIONAL_C);
        if(encounter==null||encounter.status()==EncounterStatus.COMPLETED)return;
        BlockPos town=act.otherSettlement();
        if(town==null){
            CampaignCore.LOGGER.warn("settlement_raid_not_configured town=null");
            return;
        }
        // Pick a raid-composition candidate from the pool; fall back to the native regional_boss_c roster.
        dev.campaigncore.washedashore.encounter.EncounterCandidate candidate=null;
        if(CampaignServerConfig.preferRpgMinibossesSettlementRaid()
                &&EncounterManager.selector().modPresent(RPG_MINIBOSSES_MOD))
            candidate=EncounterManager.selector().selectCandidate(level,EncounterManager.REGIONAL_C,town,RPG_MINIBOSSES_RAID)
                    .filter(dev.campaigncore.washedashore.encounter.EncounterCandidate::isRaid).orElse(null);
        if(candidate==null)candidate=EncounterManager.selector().select(level,EncounterManager.REGIONAL_C,town)
                .filter(dev.campaigncore.washedashore.encounter.EncounterCandidate::isRaid).orElse(null);
        java.util.List<EncounterDefinition.RaidMember> roster;
        if(candidate!=null){
            roster=candidate.raid();
            encounter.setSelectedCandidate(candidate.id());
            CampaignCore.LOGGER.info("settlement_raid_candidate town={} candidate={}",town,candidate.id());
        }else{
            EncounterDefinition def=EncounterManager.definitions().get(EncounterManager.REGIONAL_C).orElse(null);
            if(def==null||!def.isRaid()){
                CampaignCore.LOGGER.warn("settlement_raid_not_configured town={} has_roster={}",town,def!=null&&def.isRaid());
                return;
            }
            roster=def.raid();
        }
        // Anchor the encounter to the built town so the completion distance check is accurate.
        encounter.relocate(town,town.above());
        int spawned=spawnRaid(level,roster,candidate,town,act);
        // Recorded here — when the raid actually starts — rather than as the location trigger's
        // one-shot discover gate: a pre-crossing visit must not consume the trigger for good.
        progress.discover(CampaignCore.washedAshoreId("distant_settlement"));
        act.completedWorldObjectives().add(RAID_STARTED);
        data.dirty();
        if(spawned==0){
            // Keep the empty raid from being mistaken for a victory. The encounter cooldown will clear
            // these world flags and offer a fresh attempt after its configured retry interval.
            act.completedWorldObjectives().add(RAID_UNAVAILABLE);
            encounter.fail(level.getGameTime());
            data.dirty();
            CampaignCore.LOGGER.error("settlement_raid_spawned_none town={}; check RPG Minibosses is installed and the raid entity ids are correct",town);
            return;
        }
        act.setSettlementRaidStartTick(level.getGameTime());
        act.setSettlementRaidInitialCount(spawned);
        data.dirty();
        for(ServerPlayer nearby:level.players())
            if(nearby.blockPosition().distSqr(town)<=square(RAID_SCAN_RADIUS))
                CampaignMessages.send(nearby,"settlement_raid_begins",SettlementDialogueNames.distant(level,act));
        CampaignCore.LOGGER.info("settlement_raid_started town={} raiders={}",town,spawned);
    }

    private static int spawnRaid(ServerLevel level,java.util.List<EncounterDefinition.RaidMember> roster,
                                  dev.campaigncore.washedashore.encounter.EncounterCandidate candidate,BlockPos town,
                                  WashedAshoreInstance act){
        int spawned=0;
        for(EncounterDefinition.RaidMember member:roster){
            EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(member.entity());
            boolean missingType=type==EntityType.PIG&&!member.entity().equals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG));
            if(type==null||missingType){
                CampaignCore.LOGGER.error("settlement_raid_unknown_entity entity={}",member.entity());
                continue;
            }
            for(int i=0;i<member.count();i++)if(spawnRaider(level,type,candidate,town,act))spawned++;
        }
        return spawned;
    }

    private static boolean spawnRaider(ServerLevel level,EntityType<?> type,
                                        dev.campaigncore.washedashore.encounter.EncounterCandidate candidate,BlockPos town,
                                        WashedAshoreInstance act){
        Entity entity=type.create(level);
        if(entity==null)return false;
        CampaignSpawnProtection.protectFromSun(entity);
        double angle=level.random.nextDouble()*Math.PI*2,distance=6+level.random.nextDouble()*(RAID_SCAN_RADIUS/4.0);
        BlockPos near=town.offset(Mth.floor(Math.cos(angle)*distance),0,Mth.floor(Math.sin(angle)*distance));
        Vec3 spawn=EncounterManager.resolveClearSpawn(level,entity,near);
        entity.moveTo(spawn.x,spawn.y,spawn.z,level.random.nextFloat()*360,0);
        entity.addTag(RAID_MEMBER_TAG);
        entity.addTag(raidInstanceTag(act));
        if(entity instanceof Mob mob){
            mob.finalizeSpawn(level,level.getCurrentDifficultyAt(BlockPos.containing(spawn)),MobSpawnType.EVENT,null);
            // Persist so raiders are never counted as "gone" merely because the town chunks unloaded.
            mob.setPersistenceRequired();
            var nearest=level.getNearestPlayer(mob,RAID_SCAN_RADIUS);
            if(nearest!=null&&mob.getTarget()==null)mob.setTarget(nearest);
        }
        // Apply the candidate's attribute/loot overrides uniformly to each raider (no-op when unset).
        if(candidate!=null){
            EncounterManager.selector().applyOverrides(entity,candidate);
            if(candidate.suppressNativeDrops())entity.addTag(EncounterManager.SUPPRESS_DROPS_TAG);
        }
        // The nearest defender's act prestige levels the raider (after overrides; no-op at 0).
        if(entity instanceof Mob mob&&level.getNearestPlayer(mob,RAID_SCAN_RADIUS) instanceof ServerPlayer trigger)
            dev.campaigncore.prestige.PrestigeManager.applyDifficulty(mob,
                    dev.campaigncore.prestige.PrestigeManager.level(
                            WashedAshoreSavedData.get(level),trigger.getUUID(),CampaignCore.WASHED_ASHORE));
        return level.addFreshEntity(entity);
    }

    /** Completion check: succeed the encounter once no tagged raider remains alive near the town. */
    public static void tick(ServerLevel level,WashedAshoreSavedData data){
        for(WashedAshoreInstance act:data.instances())tick(level,data,act);
    }
    private static void tick(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act){
        if(!act.completedWorldObjectives().contains(RAID_STARTED)
                ||act.completedWorldObjectives().contains(RAID_UNAVAILABLE))return;
        EncounterAnchor encounter=act.encounters().get(EncounterManager.REGIONAL_C);
        if(encounter==null||encounter.status()!=EncounterStatus.DORMANT)return;
        if(EncounterManager.failWhenUnattended(level,data,encounter)){
            reset(level,data,act);
            return;
        }
        BlockPos town=act.otherSettlement();
        if(town==null||!level.hasChunkAt(town))return;
        // Only judge the raid cleared while a player is present, so an unloaded town is never mistaken
        // for a repelled one. Raiders are persistent, so any survivor is still in the world.
        boolean playerNear=level.players().stream().anyMatch(p->p.blockPosition().distSqr(town)<=square(RAID_SCAN_RADIUS));
        if(!playerNear)return;
        AABB area=new AABB(town).inflate(RAID_SCAN_RADIUS);
        java.util.List<LivingEntity> survivors=level.getEntitiesOfClass(LivingEntity.class,area,
                e->e.isAlive()&&belongsToRaid(e,act));
        if(!survivors.isEmpty()){
            // Backfill tracking for a raid saved by an older version. Its current survivors become
            // the baseline, avoiding an immediate completion based on missing historical state.
            if(act.settlementRaidStartTick()<0||act.settlementRaidInitialCount()<=0){
                act.setSettlementRaidStartTick(level.getGameTime());
                act.setSettlementRaidInitialCount(survivors.size());
                data.dirty();
                return;
            }
            long elapsed=level.getGameTime()-act.settlementRaidStartTick();
            boolean moreThanHalfDepleted=survivors.size()*2<act.settlementRaidInitialCount();
            if(elapsed<MERCY_COMPLETION_DELAY_TICKS||!moreThanHalfDepleted)return;
            for(LivingEntity raider:survivors)raider.discard();
            if(EncounterManager.complete(level,data,encounter)){
                announceRepelled(level,act,town);
                CampaignCore.LOGGER.info("settlement_raid_repelled_by_attrition town={} elapsed_ticks={} initial={} remaining={}",
                        town,elapsed,act.settlementRaidInitialCount(),survivors.size());
            }
            return;
        }
        if(EncounterManager.complete(level,data,encounter)){
            announceRepelled(level,act,town);
            CampaignCore.LOGGER.info("settlement_raid_repelled town={}",town);
        }
    }

    private static void announceRepelled(ServerLevel level,WashedAshoreInstance act,BlockPos town){
        for(ServerPlayer nearby:level.players())if(nearby.blockPosition().distSqr(town)<=square(RAID_SCAN_RADIUS))
            CampaignMessages.send(nearby,"settlement_raid_repelled",SettlementDialogueNames.distant(level,act));
    }

    /** Clears the world-level raid flags so the settlement raid can be re-triggered (used by debug abort). */
    public static void reset(ServerLevel level,WashedAshoreSavedData data){
        reset(level,data,data.act());
    }
    /** Clears only the physical settlement raid owned by the supplied campaign layout. */
    public static void reset(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act){
        BlockPos town=act.otherSettlement();
        if(town!=null&&level.hasChunkAt(town)){
            AABB area=new AABB(town).inflate(RAID_SCAN_RADIUS);
            for(LivingEntity raider:level.getEntitiesOfClass(LivingEntity.class,area,e->belongsToRaid(e,act)))
                raider.discard();
        }
        act.completedWorldObjectives().remove(RAID_STARTED);
        act.completedWorldObjectives().remove(RAID_UNAVAILABLE);
        act.setSettlementRaidStartTick(-1);
        act.setSettlementRaidInitialCount(0);
        data.dirty();
    }

    private static String raidInstanceTag(WashedAshoreInstance act){return RAID_INSTANCE_TAG+act.actInstanceId();}
    private static boolean belongsToRaid(LivingEntity entity,WashedAshoreInstance act){
        if(!entity.getTags().contains(RAID_MEMBER_TAG))return false;
        boolean hasOwner=entity.getTags().stream().anyMatch(tag->tag.startsWith(RAID_INSTANCE_TAG));
        return !hasOwner||entity.getTags().contains(raidInstanceTag(act));
    }

    private static double square(double value){return value*value;}
}
