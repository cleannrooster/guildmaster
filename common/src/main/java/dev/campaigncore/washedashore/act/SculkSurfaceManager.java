package dev.campaigncore.washedashore.act;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.prestige.PrestigeChallenges;
import dev.campaigncore.prestige.PrestigeManager;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Sculk Surface encounter. An arena far beyond the Dark Forest is converted to sculk and
 * seeded with sensors and shriekers once a player who has cleared all three regional objectives first draws
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
    private static final String ARENA_INSTANCE_TAG="campaign_core_washed_ashore_sculk_arena_instance=";
    /** One-time flag: the sculk swath has been converted for this world. */
    private static final net.minecraft.resources.ResourceLocation FORMED=CampaignCore.washedAshoreId("sculk_surface_formed");
    /** Coverage-version flag; old sparse arenas are re-formed once under the continuous-floor layout. */
    private static final net.minecraft.resources.ResourceLocation FORMED_FULL=CampaignCore.washedAshoreId("sculk_surface_formed_full_v2");
    /** How far beyond the formation an eligible player must come for the ground to convert. */
    private static final int FORM_TRIGGER_MARGIN=40;
    /** Minimum spacing between blight-spread lines as the kill meter climbs (cosmetic pacing only,
     *  so the per-arena last-broadcast tick is transient rather than persisted). */
    private static final long BLIGHT_MESSAGE_COOLDOWN_TICKS=8*20;
    private static final java.util.Map<UUID,Long> LAST_BLIGHT_MESSAGE_TICK=new java.util.HashMap<>();

    private SculkSurfaceManager(){}

    private static SculkArenaProfile profile(){
        return EncounterManager.definitions().get(EncounterManager.SCULK_SURFACE).map(EncounterDefinition::sculk).orElse(null);
    }

    public static void tick(ServerLevel level,WashedAshoreSavedData data){
        for(WashedAshoreInstance act:data.instances())tick(level,data,act);
    }

    private static void tick(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act){
        BlockPos center=act.sculkSurface();
        SculkArenaProfile profile=profile();
        if(center==null||profile==null)return;
        EncounterAnchor encounter=act.encounters().get(EncounterManager.SCULK_SURFACE);
        if(encounter==null)return;
        if(!level.hasChunkAt(center))return;
        if(!act.completedWorldObjectives().contains(FORMED_FULL)){
            ServerPlayer eligible=eligiblePlayerNear(level,data,center,profile.formationRadius()+FORM_TRIGGER_MARGIN);
            if(eligible!=null)formArena(level,data,act,center,profile,eligible);
            return;
        }
        if(encounter.status()!=EncounterStatus.DORMANT)return;
        if(act.sculkEncounterStartTick()>0){
            if(EncounterManager.failWhenUnattended(level,data,encounter)){
                UUID raven=act.sculkRaven();
                if(raven!=null&&level.getEntity(raven)==null)encounter.markBossForRemoval(raven);
                reset(level,data,act);
                return;
            }
            tickWaves(level,data,act,center,profile);
        }
    }

    /** Counts mob deaths on a dormant formation and, mid-fight, resolves the Raven's death. */
    public static void onMobDeath(ServerLevel level,Entity entity,DamageSource source){
        if(!(entity instanceof LivingEntity dead))return;
        WashedAshoreSavedData data=WashedAshoreSavedData.get(level);
        WashedAshoreInstance act=instanceForDeath(data,dead,profile());
        if(act==null)return;
        BlockPos center=act.sculkSurface();
        SculkArenaProfile profile=profile();
        if(center==null||profile==null)return;
        EncounterAnchor encounter=act.encounters().get(EncounterManager.SCULK_SURFACE);
        if(encounter==null||encounter.status()==EncounterStatus.COMPLETED)return;
        // The Raven itself: the arena is only won when a player is credited with the kill.
        if(dead.getUUID().equals(act.sculkRaven())){
            onRavenDeath(level,data,act,center,profile,dead,source);
            return;
        }
        if(!act.completedWorldObjectives().contains(FORMED_FULL)||act.sculkEncounterStartTick()>0)return;
        if(eligiblePlayerNear(level,data,center,profile.scanRadius())==null)return;
        if(!countable(dead)||dead.blockPosition().distSqr(center)>square(profile.scanRadius()))return;
        int deaths=act.sculkMobDeaths()+1;
        act.setSculkMobDeaths(deaths);
        data.dirty();
        if(deaths>=profile.mobDeathsToTrigger())startEncounter(level,data,act,center,profile);
        else announceBlightSpread(level,act,center,profile);
    }

    /** The catalysed ground answers each counted death, paced so a massacre doesn't spam the line. */
    private static void announceBlightSpread(ServerLevel level,WashedAshoreInstance act,BlockPos center,SculkArenaProfile profile){
        long now=level.getGameTime();
        Long last=LAST_BLIGHT_MESSAGE_TICK.get(act.actInstanceId());
        if(last!=null&&now-last<BLIGHT_MESSAGE_COOLDOWN_TICKS)return;
        LAST_BLIGHT_MESSAGE_TICK.put(act.actInstanceId(),now);
        broadcast(level,center,profile.scanRadius(),"sculk_blight_spreads");
    }

    private static void onRavenDeath(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act,BlockPos center,
                                     SculkArenaProfile profile,LivingEntity raven,DamageSource source){
        if(killedByPlayer(raven,source)){
            act.setSculkRaven(null);
            clearDraugrs(level,act,center,profile);
            data.dirty();
            EncounterAnchor encounter=act.encounters().get(EncounterManager.SCULK_SURFACE);
            if(encounter!=null)EncounterManager.complete(level,data,encounter);
            CampaignCore.LOGGER.info("sculk_raven_defeated center={} credit={}",center,creditName(raven,source));
            // Prestige resolution must follow complete(): the invoker's pre-wipe progress still has
            // the boss defeated, which keeps their fresh character out of the completion credit above.
            resolvePrestige(level,data,act,center,profile);
        }else{
            // Not a player kill (void, fire, another mob): the Raven rises anew so only a player can end it.
            UUID replacement=spawnRaven(level,act,center,profile);
            act.setSculkRaven(replacement);
            if(replacement==null){
                EncounterAnchor encounter=act.encounters().get(EncounterManager.SCULK_SURFACE);
                if(encounter!=null)encounter.fail(level.getGameTime());
                CampaignCore.LOGGER.error("sculk_raven_recreation_failed center={}; retry scheduled",center);
            }
            data.dirty();
            CampaignCore.LOGGER.info("sculk_raven_recreated center={} reason=no_player_credit new={}",center,replacement);
        }
    }

    /** True when the player finished this act and holds its Fragment of Blight (prestige re-challenge). */
    public static boolean isPrestigeChallenger(ServerPlayer player,WashedAshoreProgress progress){
        Item fragment=PrestigeChallenges.fragment(CampaignCore.WASHED_ASHORE);
        return fragment!=null&&progress.defeatedBosses().contains(EncounterManager.SCULK_SURFACE)
                &&(player.getMainHandItem().is(fragment)||player.getOffhandItem().is(fragment));
    }
    /**
     * The act prestige this arena's spawns scale by: the invoker's on a prestige re-challenge,
     * else the triggering (nearest eligible) player's — so a post-wipe replay of the story fight
     * carries the replayer's earned levels.
     */
    private static int fightPrestige(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act,
                                     BlockPos center,SculkArenaProfile profile){
        UUID invoker=act.sculkPrestigeInvoker();
        if(invoker!=null)return PrestigeManager.level(data,invoker,CampaignCore.WASHED_ASHORE);
        ServerPlayer trigger=eligiblePlayerNear(level,data,center,profile.scanRadius());
        return trigger==null?0:PrestigeManager.level(data,trigger.getUUID(),CampaignCore.WASHED_ASHORE);
    }
    private static ServerPlayer prestigeChallengerNear(ServerLevel level,WashedAshoreSavedData data,BlockPos center,int radius){
        double radiusSq=square(radius);
        for(ServerPlayer player:level.players()){
            if(player.blockPosition().distSqr(center)>radiusSq)continue;
            if(isPrestigeChallenger(player,data.player(player.getUUID())))return player;
        }
        return null;
    }
    private static boolean consumeHeldFragment(ServerPlayer player,Item item){
        if(player.getMainHandItem().is(item)){player.getMainHandItem().shrink(1);return true;}
        if(player.getOffhandItem().is(item)){player.getOffhandItem().shrink(1);return true;}
        return false;
    }
    /**
     * Marks the rising fight as a prestige re-challenge when a fragment holder is the reason it can
     * start. Players still owed a normal first completion take priority: their fight would begin
     * regardless, so the challenger's fragment is not burned on it.
     */
    private static void beginPrestigeInvocation(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act,
                                                BlockPos center,SculkArenaProfile profile){
        ServerPlayer challenger=prestigeChallengerNear(level,data,center,profile.scanRadius());
        if(challenger==null)return;
        double radiusSq=square(profile.scanRadius());
        for(ServerPlayer player:level.players()){
            if(player.blockPosition().distSqr(center)>radiusSq)continue;
            WashedAshoreProgress progress=data.player(player.getUUID());
            if(EncounterManager.hasCompletedRequiredRegionalObjectives(progress)
                    &&!progress.defeatedBosses().contains(EncounterManager.SCULK_SURFACE))return;
        }
        Item fragment=PrestigeChallenges.fragment(CampaignCore.WASHED_ASHORE);
        if(fragment==null||!consumeHeldFragment(challenger,fragment))return;
        act.setSculkPrestigeInvoker(challenger.getUUID());
        broadcast(level,center,profile.scanRadius(),"blight_challenge");
        CampaignCore.LOGGER.info("blight_prestige_invoked player={} center={} prestige={}",
                challenger.getUUID(),center,PrestigeManager.level(data,challenger.getUUID(),CampaignCore.WASHED_ASHORE));
    }

    /**
     * Settles a won prestige re-challenge. Any player's killing blow counts, but only for an
     * invoker who witnessed it: online, in this dimension, inside the encounter's reset radius.
     * An absent invoker forfeits — no prestige, no wipe, and the fragment stays spent.
     */
    private static void resolvePrestige(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act,
                                        BlockPos center,SculkArenaProfile profile){
        UUID invokerId=act.sculkPrestigeInvoker();
        if(invokerId==null)return;
        act.setSculkPrestigeInvoker(null);
        data.dirty();
        EncounterAnchor encounter=act.encounters().get(EncounterManager.SCULK_SURFACE);
        ServerPlayer invoker=level.getServer().getPlayerList().getPlayer(invokerId);
        boolean present=invoker!=null&&invoker.serverLevel()==level&&encounter!=null
                &&invoker.blockPosition().distSqr(encounter.anchorPos())<=square(encounter.resetRadius());
        if(present)PrestigeManager.award(level,data,invoker,CampaignCore.WASHED_ASHORE);
        else{
            broadcast(level,center,profile.scanRadius(),"blight_fizzled");
            CampaignCore.LOGGER.info("blight_prestige_fizzled invoker={} online={}",invokerId,invoker!=null);
        }
    }

    private static void startEncounter(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act,BlockPos center,SculkArenaProfile profile){
        if(act.sculkEncounterStartTick()>0)return;
        beginPrestigeInvocation(level,data,act,center,profile);
        // Pick a single-entity sculk candidate once (persisted on the encounter); resolved in spawnRaven.
        EncounterAnchor encounter=act.encounters().get(EncounterManager.SCULK_SURFACE);
        if(encounter!=null&&preferNativeRaven(level)){
            // Server config prefers the Sculk and Scavenge Raven: take the native spawn path, overriding
            // any pool candidate persisted before the preference could be honored.
            if(encounter.selectedCandidate()!=null){
                CampaignCore.LOGGER.info("sculk_candidate_overridden_by_preferred_raven center={} was={}",
                        center,encounter.selectedCandidate());
                encounter.setSelectedCandidate(null);
                data.dirty();
            }
        }else if(encounter!=null&&encounter.selectedCandidate()==null){
            EncounterManager.selector().select(level,EncounterManager.SCULK_SURFACE,center)
                    .filter(dev.campaigncore.washedashore.encounter.EncounterCandidate::isSingle)
                    .ifPresent(c->{encounter.setSelectedCandidate(c.id());
                        CampaignCore.LOGGER.info("sculk_candidate center={} candidate={}",center,c.id());});
        }
        UUID raven=spawnRaven(level,act,center,profile);
        if(raven==null){
            if(encounter!=null)encounter.fail(level.getGameTime());
            data.dirty();
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
    private static void tickWaves(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act,BlockPos center,SculkArenaProfile profile){
        if(profile.waveCount()<=0||profile.waveSize()<=0)return;
        long elapsed=level.getGameTime()-act.sculkEncounterStartTick();
        if(elapsed<0)return;
        int due=profile.waveDelayTicks()<=0?profile.waveCount()
                :(int)Math.min(profile.waveCount(),elapsed/profile.waveDelayTicks()+1);
        boolean changed=false;
        while(act.sculkWavesSpawned()<due){
            int wave=act.sculkWavesSpawned();
            int spawned=spawnWave(level,act,center,profile);
            act.setSculkWavesSpawned(wave+1);
            changed=true;
            broadcast(level,center,profile.scanRadius(),"sculk_wave",wave+1,profile.waveCount());
            CampaignCore.LOGGER.info("sculk_wave center={} wave={}/{} spawned={}",center,wave+1,profile.waveCount(),spawned);
        }
        if(changed)data.dirty();
    }

    // --- world building ---------------------------------------------------------------------------

    private static void formArena(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act,BlockPos center,
                                  SculkArenaProfile profile,ServerPlayer witness){
        int radius=profile.formationRadius();
        int converted=0;
        Random layoutRandom=new Random(level.getSeed()^center.asLong()^0x5C01C5EEDL);
        List<BlockPos> sculkFloor=new ArrayList<>();
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            if(x*x+z*z>radius*radius)continue;
            // Keep the perimeter organic, but make the playable arena unmistakably sculk-covered.
            double edge=(x*x+z*z)/(double)(radius*radius);
            if(edge>.72&&layoutRandom.nextDouble()<(edge-.72)/.28*.55)continue;
            int worldX=center.getX()+x,worldZ=center.getZ()+z;
            if(!level.hasChunkAt(new BlockPos(worldX,center.getY(),worldZ)))continue;
            BlockPos ground=new BlockPos(worldX,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,worldX,worldZ)-1,worldZ);
            if(!level.getFluidState(ground).isEmpty()||!level.getBlockState(ground).isSolid())continue;
            clearGrowth(level,ground.above());
            level.setBlock(ground,Blocks.SCULK.defaultBlockState(),2);
            sculkFloor.add(ground);
            converted++;
        }
        placeSparseSculkFeatures(level,sculkFloor,layoutRandom);
        act.completedWorldObjectives().add(FORMED);
        act.completedWorldObjectives().add(FORMED_FULL);
        data.dirty();
        if(witness!=null)CampaignMessages.send(witness,"sculk_surface_found");
        CampaignCore.LOGGER.info("sculk_surface_formed center={} radius={} converted_columns={} witness={}",
                center,radius,converted,witness==null?"operator":witness.getUUID());
    }

    /** Places a few focal blocks over the continuous sculk floor. */
    private static void placeSparseSculkFeatures(ServerLevel level,List<BlockPos> floor,Random random){
        Collections.shuffle(floor,random);
        List<BlockPos> placed=new ArrayList<>();
        int catalysts=0,shriekers=0;
        for(BlockPos ground:floor){
            if(catalysts>=2&&shriekers>=3)break;
            BlockPos on=ground.above();
            if(!level.getBlockState(on).isAir()||placed.stream().anyMatch(pos->pos.distSqr(on)<16))continue;
            if(catalysts<2){level.setBlock(on,Blocks.SCULK_CATALYST.defaultBlockState(),3);catalysts++;}
            else {level.setBlock(on,Blocks.SCULK_SHRIEKER.defaultBlockState(),3);shriekers++;}
            placed.add(on);
        }
    }

    private static void clearGrowth(ServerLevel level,BlockPos pos){
        BlockState state=level.getBlockState(pos);
        if(state.isAir())return;
        if(!state.getFluidState().isEmpty())return;
        // Only sweep away flimsy surface growth; never punch holes in real structures or logs.
        if(state.getCollisionShape(level,pos).isEmpty())level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
    }

    // --- spawning ---------------------------------------------------------------------------------

    private static UUID spawnRaven(ServerLevel level,WashedAshoreInstance act,BlockPos center,SculkArenaProfile profile){
        // Prefer the persisted single-entity candidate; otherwise the native raven scaled by the sculk profile.
        dev.campaigncore.washedashore.encounter.EncounterCandidate candidate=ravenCandidate(act);
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
        entity.addTag(arenaInstanceTag(act));
        entity.addTag("campaign_core_washed_ashore_encounter="+EncounterManager.SCULK_SURFACE);
        if(entity instanceof Mob mob){
            mob.finalizeSpawn(level,level.getCurrentDifficultyAt(BlockPos.containing(spawn)),MobSpawnType.EVENT,null);
            if(candidate!=null){
                EncounterManager.selector().applyOverrides(entity,candidate);
                if(candidate.suppressNativeDrops())entity.addTag(EncounterManager.SUPPRESS_DROPS_TAG);
            }else if(act.sculkPrestigeInvoker()==null)scale(mob,profile.healthScale(),profile.damageScale());
            // On a prestige re-challenge the profile's halving is skipped above; either way the
            // fight's prestige (invoker's on a re-challenge, else the triggering player's on a
            // post-wipe replay) piles on top of whatever base stats were just established.
            PrestigeManager.applyDifficulty(mob,fightPrestige(level,WashedAshoreSavedData.get(level),act,center,profile));
            mob.setPersistenceRequired();
            Player target=level.getNearestPlayer(mob,profile.scanRadius()*2.0);
            if(target!=null)mob.setTarget(target);
        }
        if(!level.addFreshEntity(entity))return null;
        eruption(level,spawn,true);
        return entity.getUUID();
    }
    /** The persisted single-entity candidate for the Sculk Surface slot, if one was selected. */
    private static dev.campaigncore.washedashore.encounter.EncounterCandidate ravenCandidate(WashedAshoreInstance act){
        EncounterAnchor encounter=act.encounters().get(EncounterManager.SCULK_SURFACE);
        net.minecraft.resources.ResourceLocation id=encounter==null?null:encounter.selectedCandidate();
        return id==null?null:EncounterManager.candidates().byId(id)
                .filter(dev.campaigncore.washedashore.encounter.EncounterCandidate::isSingle).orElse(null);
    }

    private static int spawnWave(ServerLevel level,WashedAshoreInstance act,BlockPos center,SculkArenaProfile profile){
        EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(profile.waveEntity());
        boolean missing=type==EntityType.PIG&&!profile.waveEntity().equals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG));
        if(type==null||missing){
            CampaignCore.LOGGER.error("sculk_wave_unknown_entity entity={}",profile.waveEntity());
            return 0;
        }
        int spawned=0;
        int prestige=fightPrestige(level,WashedAshoreSavedData.get(level),act,center,profile);
        for(int i=0;i<profile.waveSize();i++){
            Entity entity=type.create(level);
            if(entity==null)continue;
            CampaignSpawnProtection.protectFromSun(entity);
            double angle=level.random.nextDouble()*Math.PI*2,distance=3+level.random.nextDouble()*Math.max(1,profile.formationRadius()-3);
            BlockPos near=center.offset(Mth.floor(Math.cos(angle)*distance),0,Mth.floor(Math.sin(angle)*distance));
            Vec3 spawn=EncounterManager.resolveClearSpawn(level,entity,near);
            entity.moveTo(spawn.x,spawn.y,spawn.z,level.random.nextFloat()*360,0);
            entity.addTag(DRAUGR_TAG);
            entity.addTag(arenaInstanceTag(act));
            if(entity instanceof Mob mob){
                mob.finalizeSpawn(level,level.getCurrentDifficultyAt(BlockPos.containing(spawn)),MobSpawnType.EVENT,null);
                PrestigeManager.applyDifficulty(mob,prestige);
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

    private static void clearDraugrs(ServerLevel level,WashedAshoreInstance act,BlockPos center,SculkArenaProfile profile){
        AABB area=new AABB(center).inflate(profile.scanRadius()*2.0);
        for(LivingEntity draugr:level.getEntitiesOfClass(LivingEntity.class,area,
                e->e.getTags().contains(DRAUGR_TAG)&&belongsToArena(e,act)))
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
    /** Resolves a death to the one reinstanced arena that owns it, preferring an exact Raven UUID. */
    private static WashedAshoreInstance instanceForDeath(WashedAshoreSavedData data,LivingEntity dead,SculkArenaProfile profile){
        if(profile==null)return null;
        for(WashedAshoreInstance act:data.instances())
            if(dead.getUUID().equals(act.sculkRaven()))return act;
        String taggedOwner=dead.getTags().stream().filter(tag->tag.startsWith(ARENA_INSTANCE_TAG)).findFirst().orElse(null);
        if(taggedOwner!=null){
            String raw=taggedOwner.substring(ARENA_INSTANCE_TAG.length());
            try{
                UUID id=UUID.fromString(raw);
                for(WashedAshoreInstance act:data.instances())if(act.actInstanceId().equals(id))return act;
            }catch(IllegalArgumentException ignored){}
            return null;
        }
        WashedAshoreInstance nearest=null;double nearestDistance=Double.MAX_VALUE;
        for(WashedAshoreInstance act:data.instances()){
            BlockPos center=act.sculkSurface();
            if(center==null||!act.completedWorldObjectives().contains(FORMED_FULL))continue;
            double distance=dead.blockPosition().distSqr(center);
            if(distance<=square(profile.scanRadius())&&distance<nearestDistance){
                nearest=act;nearestDistance=distance;
            }
        }
        return nearest;
    }
    private static String arenaInstanceTag(WashedAshoreInstance act){return ARENA_INSTANCE_TAG+act.actInstanceId();}
    private static boolean belongsToArena(Entity entity,WashedAshoreInstance act){
        boolean hasOwner=entity.getTags().stream().anyMatch(tag->tag.startsWith(ARENA_INSTANCE_TAG));
        return !hasOwner||entity.getTags().contains(arenaInstanceTag(act));
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
    /** True when the server config prefers the native Sculk and Scavenge Raven and it can actually spawn. */
    private static boolean preferNativeRaven(ServerLevel level){
        net.minecraft.resources.ResourceLocation nativeId=ravenTypeId(level);
        return dev.campaigncore.config.CampaignServerConfig.preferSculkAndScavengeEncounters()
                &&EncounterManager.selector().modPresent(nativeId.getNamespace())
                &&dev.campaigncore.washedashore.encounter.EncounterCandidateSelector.entityResolves(nativeId);
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
        reset(level,data,data.act());
    }
    /** Clears only the arena state owned by the supplied campaign layout. */
    public static void reset(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act){
        BlockPos center=act.sculkSurface();
        SculkArenaProfile profile=profile();
        if(center!=null&&profile!=null&&level.hasChunkAt(center)){
            clearDraugrs(level,act,center,profile);
            UUID raven=act.sculkRaven();
            if(raven!=null){Entity boss=level.getEntity(raven);if(boss!=null)boss.discard();}
        }
        act.setSculkMobDeaths(0);
        act.setSculkEncounterStartTick(0);
        act.setSculkWavesSpawned(0);
        act.setSculkRaven(null);
        act.setSculkPrestigeInvoker(null);
        data.dirty();
    }

    private static double square(double value){return value*value;}

    // Exposed for debug tooling.
    public static boolean debugStart(ServerLevel level,WashedAshoreSavedData data,ServerPlayer witness){
        return debugStart(level,data,data.act(),witness);
    }
    public static boolean debugStart(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act,ServerPlayer witness){
        BlockPos center=act.sculkSurface();SculkArenaProfile profile=profile();
        if(center==null||profile==null||!level.hasChunkAt(center))return false;
        if(!act.completedWorldObjectives().contains(FORMED_FULL))formArena(level,data,act,center,profile,witness);
        startEncounter(level,data,act,center,profile);
        return act.sculkEncounterStartTick()>0;
    }
    static boolean debugForm(ServerLevel level,WashedAshoreSavedData data,ServerPlayer witness){
        return debugForm(level,data,data.act(),witness);
    }
    static boolean debugForm(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act,ServerPlayer witness){
        BlockPos center=act.sculkSurface();SculkArenaProfile profile=profile();
        if(center==null||profile==null||!level.hasChunkAt(center))return false;
        if(!act.completedWorldObjectives().contains(FORMED_FULL))formArena(level,data,act,center,profile,witness);
        return act.completedWorldObjectives().contains(FORMED_FULL);
    }
    /** Whether the sculk swath has already been converted (inspection only). */
    public static boolean isFormed(WashedAshoreSavedData data){
        return isFormed(data.act());
    }
    public static boolean isFormed(WashedAshoreInstance act){return act.completedWorldObjectives().contains(FORMED_FULL);}
}
