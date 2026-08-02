package dev.campaigncore.washedashore.act;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import dev.campaigncore.washedashore.encounter.EncounterAnchor;
import dev.campaigncore.washedashore.encounter.EncounterDefinition;
import dev.campaigncore.washedashore.encounter.EncounterManager;
import dev.campaigncore.washedashore.encounter.EncounterStatus;
import dev.campaigncore.washedashore.encounter.SculkArenaProfile;
import dev.campaigncore.washedashore.encounter.CampaignSpawnProtection;
import dev.campaigncore.washedashore.message.CampaignMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/**
 * The Sculk Surface encounter. A swath of ground far beyond the Dark Forest is converted to sculk and
 * seeded with sensors and shriekers once a player who has cleared two regional objectives first draws
 * near. The formation lies dormant until enough mobs die on it — the catalysed ground drinking their
 * deaths — at which point a Sculken Raven, halved in health and damage, tears free and is reinforced by
 * timed waves of draugrs. The fight is only won when a player lands the killing blow on the Raven.
 *
 * <p>All state is world-level and persisted on the act (mirroring {@link CrossingHordeManager} and
 * {@link SettlementRaidManager}); the roster, scaling and pacing live entirely in the encounter's
 * {@code sculk} data block ({@link SculkArenaProfile}).
 */
public final class SculkSurfaceManager {
    /** Marks the risen Sculken Raven of this arena (the arena also stores its UUID on the act). */
    public static final String RAVEN_TAG="campaign_core_washed_ashore_sculk_raven";
    /** Marks a draugr reinforcement of this arena (excluded from the mob-death trigger, cleaned on win). */
    public static final String DRAUGR_TAG="campaign_core_washed_ashore_sculk_draugr";
    /** One-time flag: the sculk swath has been converted for this world. */
    private static final net.minecraft.resources.ResourceLocation FORMED=CampaignCore.washedAshoreId("sculk_surface_formed");
    /** How far beyond the formation an eligible player must come for the ground to convert. */
    private static final int FORM_TRIGGER_MARGIN=40;

    private SculkSurfaceManager(){}

    private static SculkArenaProfile profile(){
        return EncounterManager.definitions().get(EncounterManager.SCULK_SURFACE).map(EncounterDefinition::sculk).orElse(null);
    }

    public static void tick(ServerLevel level,WashedAshoreSavedData data){
        WashedAshoreInstance act=data.act();
        BlockPos center=act.sculkSurface();
        SculkArenaProfile profile=profile();
        if(center==null||profile==null)return;
        EncounterAnchor encounter=act.encounters().get(EncounterManager.SCULK_SURFACE);
        if(encounter==null||encounter.status()==EncounterStatus.COMPLETED)return;
        if(!level.hasChunkAt(center))return;
        if(!act.completedWorldObjectives().contains(FORMED)){
            ServerPlayer eligible=eligiblePlayerNear(level,data,center,profile.formationRadius()+FORM_TRIGGER_MARGIN);
            if(eligible!=null)formArena(level,data,center,profile,eligible);
            return;
        }
        if(act.sculkEncounterStartTick()>0)tickWaves(level,data,center,profile);
    }

    /** Counts mob deaths on a dormant formation and, mid-fight, resolves the Raven's death. */
    public static void onMobDeath(ServerLevel level,Entity entity,DamageSource source){
        if(!(entity instanceof LivingEntity dead))return;
        WashedAshoreSavedData data=WashedAshoreSavedData.get(level);
        WashedAshoreInstance act=data.act();
        BlockPos center=act.sculkSurface();
        SculkArenaProfile profile=profile();
        if(center==null||profile==null)return;
        EncounterAnchor encounter=act.encounters().get(EncounterManager.SCULK_SURFACE);
        if(encounter==null||encounter.status()==EncounterStatus.COMPLETED)return;
        // The Raven itself: the arena is only won when a player is credited with the kill.
        if(dead.getUUID().equals(act.sculkRaven())){
            onRavenDeath(level,data,center,profile,dead,source);
            return;
        }
        if(!act.completedWorldObjectives().contains(FORMED)||act.sculkEncounterStartTick()>0)return;
        if(!countable(dead)||dead.blockPosition().distSqr(center)>square(profile.scanRadius()))return;
        int deaths=act.sculkMobDeaths()+1;
        act.setSculkMobDeaths(deaths);
        data.dirty();
        if(deaths>=profile.mobDeathsToTrigger())startEncounter(level,data,center,profile);
    }

    private static void onRavenDeath(ServerLevel level,WashedAshoreSavedData data,BlockPos center,
                                     SculkArenaProfile profile,LivingEntity raven,DamageSource source){
        WashedAshoreInstance act=data.act();
        if(killedByPlayer(raven,source)){
            act.setSculkRaven(null);
            clearDraugrs(level,center,profile);
            data.dirty();
            EncounterManager.complete(level,data,EncounterManager.SCULK_SURFACE);
            CampaignCore.LOGGER.info("sculk_raven_defeated center={} credit={}",center,creditName(raven,source));
        }else{
            // Not a player kill (void, fire, another mob): the Raven rises anew so only a player can end it.
            UUID replacement=spawnRaven(level,data,center,profile);
            act.setSculkRaven(replacement);
            data.dirty();
            CampaignCore.LOGGER.info("sculk_raven_recreated center={} reason=no_player_credit new={}",center,replacement);
        }
    }

    private static void startEncounter(ServerLevel level,WashedAshoreSavedData data,BlockPos center,SculkArenaProfile profile){
        WashedAshoreInstance act=data.act();
        if(act.sculkEncounterStartTick()>0)return;
        // Pick a single-entity sculk candidate once (persisted on the encounter); resolved in spawnRaven.
        EncounterAnchor encounter=act.encounters().get(EncounterManager.SCULK_SURFACE);
        if(encounter!=null&&encounter.selectedCandidate()==null){
            EncounterManager.selector().select(level,EncounterManager.SCULK_SURFACE,center)
                    .filter(dev.campaigncore.washedashore.encounter.EncounterCandidate::isSingle)
                    .ifPresent(c->{encounter.setSelectedCandidate(c.id());
                        CampaignCore.LOGGER.info("sculk_candidate center={} candidate={}",center,c.id());});
        }
        UUID raven=spawnRaven(level,data,center,profile);
        if(raven==null){
            CampaignCore.LOGGER.error("sculk_raven_spawn_failed center={} entity={}",center,ravenType(data));
            return;
        }
        act.setSculkRaven(raven);
        act.setSculkEncounterStartTick(Math.max(1,level.getGameTime()));
        act.setSculkWavesSpawned(0);
        data.dirty();
        broadcast(level,center,profile.scanRadius(),"sculk_raven_rises");
        CampaignCore.LOGGER.info("sculk_encounter_started center={} raven={} deaths={}",center,raven,act.sculkMobDeaths());
    }

    /** Releases the scheduled draugr waves as the fight ages: wave i is due {@code i*delay} ticks in. */
    private static void tickWaves(ServerLevel level,WashedAshoreSavedData data,BlockPos center,SculkArenaProfile profile){
        WashedAshoreInstance act=data.act();
        if(profile.waveCount()<=0||profile.waveSize()<=0)return;
        long elapsed=level.getGameTime()-act.sculkEncounterStartTick();
        if(elapsed<0)return;
        int due=profile.waveDelayTicks()<=0?profile.waveCount()
                :(int)Math.min(profile.waveCount(),elapsed/profile.waveDelayTicks()+1);
        boolean changed=false;
        while(act.sculkWavesSpawned()<due){
            int wave=act.sculkWavesSpawned();
            int spawned=spawnWave(level,center,profile);
            act.setSculkWavesSpawned(wave+1);
            changed=true;
            broadcast(level,center,profile.scanRadius(),"sculk_wave",wave+1,profile.waveCount());
            CampaignCore.LOGGER.info("sculk_wave center={} wave={}/{} spawned={}",center,wave+1,profile.waveCount(),spawned);
        }
        if(changed)data.dirty();
    }

    // --- world building ---------------------------------------------------------------------------

    private static void formArena(ServerLevel level,WashedAshoreSavedData data,BlockPos center,
                                  SculkArenaProfile profile,ServerPlayer witness){
        int radius=profile.formationRadius();
        int converted=0;
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            if(x*x+z*z>radius*radius)continue;
            int worldX=center.getX()+x,worldZ=center.getZ()+z;
            if(!level.hasChunkAt(new BlockPos(worldX,center.getY(),worldZ)))continue;
            BlockPos ground=new BlockPos(worldX,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,worldX,worldZ)-1,worldZ);
            if(!level.getFluidState(ground).isEmpty()||!level.getBlockState(ground).isSolid())continue;
            clearGrowth(level,ground.above());
            level.setBlock(ground,Blocks.SCULK.defaultBlockState(),2);
            BlockPos below=ground.below();
            if(level.getBlockState(below).isSolid()&&level.getFluidState(below).isEmpty())
                level.setBlock(below,Blocks.SCULK.defaultBlockState(),2);
            dress(level,ground.above());
            converted++;
        }
        // A catalyst at the heart keeps the ground drinking every death that follows.
        BlockPos heart=new BlockPos(center.getX(),level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,center.getX(),center.getZ()),center.getZ());
        level.setBlock(heart,Blocks.SCULK_CATALYST.defaultBlockState(),3);
        data.act().completedWorldObjectives().add(FORMED);
        data.dirty();
        CampaignMessages.send(witness,"sculk_surface_found");
        CampaignCore.LOGGER.info("sculk_surface_formed center={} radius={} converted_columns={} witness={}",
                center,radius,converted,witness.getUUID());
    }

    /** Scatters sensors, shriekers and sculk growths across the converted swath. */
    private static void dress(ServerLevel level,BlockPos on){
        if(!level.getBlockState(on).isAir())return;
        float r=level.random.nextFloat();
        if(r<0.04f)level.setBlock(on,Blocks.SCULK_SHRIEKER.defaultBlockState(),3);       // decorative: default can_summon=false
        else if(r<0.12f)level.setBlock(on,Blocks.SCULK_SENSOR.defaultBlockState(),3);
        else if(r<0.15f)level.setBlock(on,Blocks.SCULK_CATALYST.defaultBlockState(),3);
    }

    private static void clearGrowth(ServerLevel level,BlockPos pos){
        BlockState state=level.getBlockState(pos);
        if(state.isAir())return;
        if(!state.getFluidState().isEmpty())return;
        // Only sweep away flimsy surface growth; never punch holes in real structures or logs.
        if(state.getCollisionShape(level,pos).isEmpty())level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
    }

    // --- spawning ---------------------------------------------------------------------------------

    private static UUID spawnRaven(ServerLevel level,WashedAshoreSavedData data,BlockPos center,SculkArenaProfile profile){
        // Prefer the persisted single-entity candidate; otherwise the native raven scaled by the sculk profile.
        dev.campaigncore.washedashore.encounter.EncounterCandidate candidate=ravenCandidate(data);
        EntityType<?> type=null;
        if(candidate!=null){
            type=EncounterManager.selector().resolveEntityType(candidate,level.random).orElse(null);
            if(type==null)CampaignCore.LOGGER.warn("sculk_candidate_unresolved candidate={}",candidate.id());
        }
        if(type==null){
            net.minecraft.resources.ResourceLocation nativeId=ravenTypeId(level);
            type=BuiltInRegistries.ENTITY_TYPE.get(nativeId);
            boolean missing=type==EntityType.PIG&&!nativeId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG));
            if(type==null||missing)return null;
            candidate=null; // fell back to native: scale by profile, not candidate overrides
        }
        Entity entity=type.create(level);
        if(entity==null)return null;
        CampaignSpawnProtection.protectFromSun(entity);
        Vec3 spawn=EncounterManager.resolveClearSpawn(level,entity,center.above());
        entity.moveTo(spawn.x,spawn.y,spawn.z,level.random.nextFloat()*360,0);
        entity.addTag(RAVEN_TAG);
        entity.addTag("campaign_core_washed_ashore_encounter="+EncounterManager.SCULK_SURFACE);
        if(entity instanceof Mob mob){
            mob.finalizeSpawn(level,level.getCurrentDifficultyAt(BlockPos.containing(spawn)),MobSpawnType.EVENT,null);
            if(candidate!=null){
                EncounterManager.selector().applyOverrides(entity,candidate);
                if(candidate.suppressNativeDrops())entity.addTag(EncounterManager.SUPPRESS_DROPS_TAG);
            }else scale(mob,profile.healthScale(),profile.damageScale());
            mob.setPersistenceRequired();
            Player target=level.getNearestPlayer(mob,profile.scanRadius()*2.0);
            if(target!=null)mob.setTarget(target);
        }
        if(!level.addFreshEntity(entity))return null;
        eruption(level,spawn,true);
        return entity.getUUID();
    }
    /** The persisted single-entity candidate for the Sculk Surface slot, if one was selected. */
    private static dev.campaigncore.washedashore.encounter.EncounterCandidate ravenCandidate(WashedAshoreSavedData data){
        EncounterAnchor encounter=data.act().encounters().get(EncounterManager.SCULK_SURFACE);
        net.minecraft.resources.ResourceLocation id=encounter==null?null:encounter.selectedCandidate();
        return id==null?null:EncounterManager.candidates().byId(id)
                .filter(dev.campaigncore.washedashore.encounter.EncounterCandidate::isSingle).orElse(null);
    }

    private static int spawnWave(ServerLevel level,BlockPos center,SculkArenaProfile profile){
        EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(profile.waveEntity());
        boolean missing=type==EntityType.PIG&&!profile.waveEntity().equals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG));
        if(type==null||missing){
            CampaignCore.LOGGER.error("sculk_wave_unknown_entity entity={}",profile.waveEntity());
            return 0;
        }
        int spawned=0;
        for(int i=0;i<profile.waveSize();i++){
            Entity entity=type.create(level);
            if(entity==null)continue;
            CampaignSpawnProtection.protectFromSun(entity);
            double angle=level.random.nextDouble()*Math.PI*2,distance=3+level.random.nextDouble()*Math.max(1,profile.formationRadius()-3);
            BlockPos near=center.offset(Mth.floor(Math.cos(angle)*distance),0,Mth.floor(Math.sin(angle)*distance));
            Vec3 spawn=EncounterManager.resolveClearSpawn(level,entity,near);
            entity.moveTo(spawn.x,spawn.y,spawn.z,level.random.nextFloat()*360,0);
            entity.addTag(DRAUGR_TAG);
            if(entity instanceof Mob mob){
                mob.finalizeSpawn(level,level.getCurrentDifficultyAt(BlockPos.containing(spawn)),MobSpawnType.EVENT,null);
                mob.setPersistenceRequired();
                Player target=level.getNearestPlayer(mob,profile.scanRadius()*2.0);
                if(target!=null)mob.setTarget(target);
            }
            if(level.addFreshEntity(entity)){eruption(level,spawn,false);spawned++;}
        }
        return spawned;
    }

    private static void scale(LivingEntity living,double healthScale,double damageScale){
        AttributeInstance health=living.getAttribute(Attributes.MAX_HEALTH);
        if(health!=null){health.setBaseValue(Math.max(1,health.getBaseValue()*healthScale));living.setHealth(living.getMaxHealth());}
        scaleAttribute(living,Attributes.ATTACK_DAMAGE,damageScale);
    }
    private static void scaleAttribute(LivingEntity living,net.minecraft.core.Holder<Attribute> attribute,double factor){
        AttributeInstance instance=living.getAttribute(attribute);
        if(instance!=null)instance.setBaseValue(Math.max(0,instance.getBaseValue()*factor));
        else CampaignCore.LOGGER.warn("sculk_raven_missing_attribute uuid={} attribute={}",living.getUUID(),attribute);
    }

    private static void clearDraugrs(ServerLevel level,BlockPos center,SculkArenaProfile profile){
        AABB area=new AABB(center).inflate(profile.scanRadius()*2.0);
        for(LivingEntity draugr:level.getEntitiesOfClass(LivingEntity.class,area,e->e.getTags().contains(DRAUGR_TAG)))
            draugr.discard();
    }

    private static void eruption(ServerLevel level,Vec3 pos,boolean raven){
        level.sendParticles(ParticleTypes.SCULK_SOUL,pos.x,pos.y+.5,pos.z,raven?30:12,.6,.5,.6,.02);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,pos.x,pos.y+.3,pos.z,raven?20:8,.5,.3,.5,.01);
        level.playSound(null,BlockPos.containing(pos),raven?SoundEvents.SCULK_CATALYST_BLOOM:SoundEvents.SCULK_BLOCK_SPREAD,
                SoundSource.HOSTILE,raven?1.4f:.9f,.6f+level.random.nextFloat()*.2f);
    }

    // --- helpers ----------------------------------------------------------------------------------

    private static ServerPlayer eligiblePlayerNear(ServerLevel level,WashedAshoreSavedData data,BlockPos center,int radius){
        double radiusSq=square(radius);
        for(ServerPlayer player:level.players()){
            if(player.blockPosition().distSqr(center)>radiusSq)continue;
            if(EncounterManager.hasCompletedRequiredRegionalObjectives(data.player(player.getUUID())))return player;
        }
        return null;
    }
    private static boolean countable(LivingEntity entity){
        return !(entity instanceof Player)
                &&!entity.getTags().contains(RAVEN_TAG)&&!entity.getTags().contains(DRAUGR_TAG);
    }
    private static boolean killedByPlayer(LivingEntity dead,DamageSource source){
        if(source!=null&&source.getEntity() instanceof Player)return true;
        return dead.getKillCredit() instanceof Player;
    }
    private static String creditName(LivingEntity dead,DamageSource source){
        Entity attacker=source==null?null:source.getEntity();
        if(attacker instanceof Player player)return player.getGameProfile().getName();
        return dead.getKillCredit()!=null?dead.getKillCredit().getScoreboardName():"unknown";
    }
    private static net.minecraft.resources.ResourceLocation ravenTypeId(ServerLevel level){
        return EncounterManager.definitions().get(EncounterManager.SCULK_SURFACE)
                .map(EncounterDefinition::bossEntity).orElse(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG));
    }
    private static String ravenType(WashedAshoreSavedData data){
        return EncounterManager.definitions().get(EncounterManager.SCULK_SURFACE)
                .map(d->d.bossEntity().toString()).orElse("unknown");
    }
    private static void broadcast(ServerLevel level,BlockPos center,int radius,String id,Object...args){
        double radiusSq=square(radius);
        for(ServerPlayer player:level.players())
            if(player.blockPosition().distSqr(center)<=radiusSq)CampaignMessages.send(player,id,args);
    }

    /** Clears the arena's world-level state so the encounter can be re-triggered from scratch. */
    public static void reset(ServerLevel level,WashedAshoreSavedData data){
        WashedAshoreInstance act=data.act();
        BlockPos center=act.sculkSurface();
        SculkArenaProfile profile=profile();
        if(center!=null&&profile!=null&&level.hasChunkAt(center)){
            clearDraugrs(level,center,profile);
            UUID raven=act.sculkRaven();
            if(raven!=null){Entity boss=level.getEntity(raven);if(boss!=null)boss.discard();}
        }
        act.setSculkMobDeaths(0);
        act.setSculkEncounterStartTick(0);
        act.setSculkWavesSpawned(0);
        act.setSculkRaven(null);
        data.dirty();
    }

    private static double square(double value){return value*value;}

    // Exposed for debug tooling.
    public static boolean debugStart(ServerLevel level,WashedAshoreSavedData data,ServerPlayer witness){
        BlockPos center=data.act().sculkSurface();SculkArenaProfile profile=profile();
        if(center==null||profile==null||!level.hasChunkAt(center))return false;
        if(!data.act().completedWorldObjectives().contains(FORMED))formArena(level,data,center,profile,witness);
        startEncounter(level,data,center,profile);
        return data.act().sculkEncounterStartTick()>0;
    }
    static boolean debugForm(ServerLevel level,WashedAshoreSavedData data,ServerPlayer witness){
        BlockPos center=data.act().sculkSurface();SculkArenaProfile profile=profile();
        if(center==null||profile==null||!level.hasChunkAt(center))return false;
        if(!data.act().completedWorldObjectives().contains(FORMED))formArena(level,data,center,profile,witness);
        return data.act().completedWorldObjectives().contains(FORMED);
    }
    /** Whether the sculk swath has already been converted (inspection only). */
    public static boolean isFormed(WashedAshoreSavedData data){
        return data.act().completedWorldObjectives().contains(FORMED);
    }
}
