package dev.campaigncore.washedashore.act;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import dev.campaigncore.washedashore.encounter.EncounterAnchor;
import dev.campaigncore.washedashore.encounter.EncounterDefinition;
import dev.campaigncore.washedashore.encounter.EncounterManager;
import dev.campaigncore.washedashore.encounter.EncounterStatus;
import dev.campaigncore.washedashore.encounter.HordeProfile;
import dev.campaigncore.washedashore.encounter.CampaignSpawnProtection;
import dev.campaigncore.washedashore.message.CampaignMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Temporary Devil's Crossing encounter: once the investigation meter fills, the churned ground erupts
 * and a horde of husks and skeletons claws its way up in successive waves, each rising only after the
 * previous one is put down. This stands in for the authored Thrasher boss while it is unfinished — the
 * roster and pacing live entirely in the {@code thrasher} encounter's {@code horde} block, so restoring
 * the boss is a pure-data change (drop the block, repoint {@code on_full.action}).
 *
 * <p>Every risen mob is capped with a helmet so none of them burn away in daylight, matching the
 * "drowned dead that the sun cannot touch" framing. State is world-level and mirrors
 * {@link SettlementRaidManager}: a one-shot start flag plus a persisted wave counter on the act, with
 * completion judged by scanning for surviving horde members near the crossing.
 */
public final class CrossingHordeManager {
    /** Marks a mob as part of the Devil's Crossing horde (read by the clear-check below). */
    public static final String HORDE_MEMBER_TAG="campaign_core_washed_ashore_crossing_horde";
    /** One-time flag: the horde has begun rising for this world. */
    private static final ResourceLocation HORDE_STARTED=CampaignCore.washedAshoreId("crossing_horde_started");

    private CrossingHordeManager(){}

    /** Begins the horde: arms the world-level state so {@link #tick} raises the first wave next pass. */
    public static void begin(ServerLevel level,WashedAshoreSavedData data,EncounterAnchor encounter,ServerPlayer player){
        WashedAshoreInstance act=data.act();
        if(encounter.status()==EncounterStatus.COMPLETED||encounter.status()==EncounterStatus.ACTIVE
                ||act.completedWorldObjectives().contains(HORDE_STARTED))return;
        // If a single large creature is eligible from the pool, let it emerge as the boss (tracked by
        // EncounterManager) instead of the native wave horde. Environment weighting decides which (sandy
        // favors worms, water aquatic creatures, cold Frostmaw, etc.); its data lives on the candidates.
        dev.campaigncore.washedashore.encounter.EncounterCandidate single=
                EncounterManager.selector().select(level,encounter.encounterId(),encounter.bossSpawnPos())
                        .filter(dev.campaigncore.washedashore.encounter.EncounterCandidate::isSingle).orElse(null);
        if(single!=null){
            encounter.setSelectedCandidate(single.id());
            if(EncounterManager.activate(level,data,encounter,player,false)){
                data.dirty();
                BlockPos where=act.devilsCrossing();
                for(ServerPlayer nearby:level.players())
                    if(where==null||nearby.blockPosition().distSqr(where)<=square(64))
                        CampaignMessages.send(nearby,"crossing_horde_rises");
                CampaignCore.LOGGER.info("crossing_creature_emerged crossing={} candidate={}",where,single.id());
                return;
            }
            encounter.setSelectedCandidate(null); // emerge failed; fall back to the native horde below
        }
        EncounterDefinition def=EncounterManager.definitions().get(encounter.encounterId()).orElse(null);
        if(def==null||!def.isHorde()){
            CampaignCore.LOGGER.warn("crossing_horde_not_configured encounter={} has_horde={}",encounter.encounterId(),def!=null&&def.isHorde());
            return;
        }
        act.setCrossingHordeWave(0);
        act.completedWorldObjectives().add(HORDE_STARTED);
        data.dirty();
        BlockPos crossing=act.devilsCrossing();
        for(ServerPlayer nearby:level.players())
            if(crossing==null||nearby.blockPosition().distSqr(crossing)<=square(def.horde().scanRadius()))
                CampaignMessages.send(nearby,"crossing_horde_rises");
        CampaignCore.LOGGER.info("crossing_horde_started crossing={} waves={}",crossing,def.horde().waveCount());
    }

    /** Wave pump: raises the next wave once the field is clear, and completes the encounter after the last. */
    public static void tick(ServerLevel level,WashedAshoreSavedData data){
        WashedAshoreInstance act=data.act();
        if(!act.completedWorldObjectives().contains(HORDE_STARTED))return;
        EncounterAnchor encounter=act.encounters().get(EncounterManager.THRASHER);
        if(encounter==null||encounter.status()==EncounterStatus.COMPLETED)return;
        EncounterDefinition def=EncounterManager.definitions().get(EncounterManager.THRASHER).orElse(null);
        if(def==null||!def.isHorde())return;
        HordeProfile horde=def.horde();
        BlockPos crossing=act.devilsCrossing();
        if(crossing==null||!level.hasChunkAt(crossing))return;
        // Only judge (and advance) the horde while a player is present, so an unloaded crossing is never
        // mistaken for a cleared field. Members are persistent, so any survivor is still in the world.
        ServerPlayer near=nearestPlayer(level,crossing,horde.scanRadius());
        if(near==null)return;
        if(hasSurvivor(level,crossing,horde.scanRadius()))return;
        int wave=act.crossingHordeWave();
        if(wave<horde.waveCount()){
            int spawned=spawnWave(level,horde,horde.waves().get(wave),near);
            act.setCrossingHordeWave(wave+1);
            data.dirty();
            announceWave(level,crossing,horde.scanRadius(),wave+1,horde.waveCount());
            CampaignCore.LOGGER.info("crossing_horde_wave crossing={} wave={}/{} spawned={}",crossing,wave+1,horde.waveCount(),spawned);
        }else{
            if(EncounterManager.complete(level,data,EncounterManager.THRASHER))
                CampaignCore.LOGGER.info("crossing_horde_cleared crossing={} waves={}",crossing,horde.waveCount());
        }
    }

    private static int spawnWave(ServerLevel level,HordeProfile horde,HordeProfile.Wave wave,ServerPlayer around){
        int spawned=0;
        for(EncounterDefinition.RaidMember member:wave.members()){
            EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(member.entity());
            boolean missingType=type==EntityType.PIG&&!member.entity().equals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG));
            if(type==null||missingType){
                CampaignCore.LOGGER.error("crossing_horde_unknown_entity entity={}",member.entity());
                continue;
            }
            for(int i=0;i<member.count();i++)if(raise(level,type,around,horde.spawnRadius()))spawned++;
        }
        return spawned;
    }

    /** Spawns one horde member erupting from the ground near the player, sun-proofed and hunting. */
    private static boolean raise(ServerLevel level,EntityType<?> type,ServerPlayer around,int spawnRadius){
        Entity entity=type.create(level);
        if(entity==null)return false;
        CampaignSpawnProtection.protectFromSun(entity);
        double angle=level.random.nextDouble()*Math.PI*2,distance=3+level.random.nextDouble()*Math.max(1,spawnRadius-3);
        BlockPos target=around.blockPosition().offset(Mth.floor(Math.cos(angle)*distance),0,Mth.floor(Math.sin(angle)*distance));
        Vec3 spawn=EncounterManager.resolveClearSpawn(level,entity,target);
        entity.moveTo(spawn.x,spawn.y,spawn.z,level.random.nextFloat()*360,0);
        entity.addTag(HORDE_MEMBER_TAG);
        if(entity instanceof Mob mob){
            mob.finalizeSpawn(level,level.getCurrentDifficultyAt(BlockPos.containing(spawn)),MobSpawnType.EVENT,null);
            sunProof(mob);
            // Persist so risen dead are never counted as "gone" merely because the crossing chunks unloaded.
            mob.setPersistenceRequired();
            if(mob.getTarget()==null&&around.isAlive())mob.setTarget(around);
        }
        if(!level.addFreshEntity(entity))return false;
        eruption(level,spawn);
        return true;
    }

    /** Caps a mob so daylight can't ignite it — the crossing dead rise and stay whatever the hour. */
    private static void sunProof(Mob mob){
        if(!mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty())return;
        mob.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));
        mob.setDropChance(EquipmentSlot.HEAD,0f);
    }

    private static void eruption(ServerLevel level,Vec3 pos){
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.DIRT.defaultBlockState()),
                pos.x,pos.y+.1,pos.z,30,.35,.1,.35,.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,pos.x,pos.y+.3,pos.z,8,.3,.2,.3,.01);
        level.playSound(null,BlockPos.containing(pos),SoundEvents.ROOTED_DIRT_BREAK,SoundSource.HOSTILE,1.2f,.6f+level.random.nextFloat()*.2f);
    }

    private static void announceWave(ServerLevel level,BlockPos crossing,int scanRadius,int wave,int total){
        for(ServerPlayer player:level.players())
            if(player.blockPosition().distSqr(crossing)<=square(scanRadius))
                CampaignMessages.send(player,"crossing_wave",wave,total);
    }

    private static boolean hasSurvivor(ServerLevel level,BlockPos crossing,int scanRadius){
        AABB area=new AABB(crossing).inflate(scanRadius);
        return !level.getEntitiesOfClass(LivingEntity.class,area,
                e->e.isAlive()&&e.getTags().contains(HORDE_MEMBER_TAG)).isEmpty();
    }

    private static ServerPlayer nearestPlayer(ServerLevel level,BlockPos crossing,int scanRadius){
        ServerPlayer best=null;double bestSq=square(scanRadius);
        for(ServerPlayer player:level.players()){
            double d=player.blockPosition().distSqr(crossing);
            if(d<=bestSq){bestSq=d;best=player;}
        }
        return best;
    }

    /** Clears the world-level horde state so the crossing encounter can be retried from scratch. */
    public static void reset(WashedAshoreSavedData data){
        data.act().completedWorldObjectives().remove(HORDE_STARTED);
        data.act().setCrossingHordeWave(0);
        data.dirty();
    }

    private static double square(double value){return value*value;}
}
