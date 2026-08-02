package dev.campaigncore.washedashore.act;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import dev.campaigncore.washedashore.encounter.ActivationCompletion;
import dev.campaigncore.washedashore.encounter.ActivationMilestone;
import dev.campaigncore.washedashore.encounter.ActivationProfile;
import dev.campaigncore.washedashore.encounter.EncounterAnchor;
import dev.campaigncore.washedashore.encounter.EncounterDefinition;
import dev.campaigncore.washedashore.encounter.EncounterManager;
import dev.campaigncore.washedashore.encounter.EncounterStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import dev.campaigncore.washedashore.message.SettlementDialogueNames;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.campaigncore.washedashore.message.CampaignMessages;
import dev.campaigncore.washedashore.registry.CampaignItems;

/** Explicit event-driven state for the two authored Act 1 regional investigations. */
public final class RegionalQuestManager {
    private static final int LIVESTOCK_DEATH_DREAD=30;
    private static final double LOOK_DOWN_THRESHOLD=-.35;
    private static final int PREY_SEARCH_HEIGHT=12;
    /** One-time flag: the dread grove has been stocked with livestock so the event can build. */
    private static final ResourceLocation DREAD_PREY_SEEDED=CampaignCore.washedAshoreId("dread_prey_seeded");
    /** Livestock is scattered within this horizontal radius (blocks) of the dread POI when seeded. */
    private static final int DREAD_PREY_SPAWN_RADIUS=14;
    /** The Thrasher emerges this many blocks from the investigating player (not at the fixed anchor). */
    private static final int THRASHER_SPAWN_DISTANCE=6;
    private static final java.util.Set<String> MILESTONE_MODES=java.util.Set.of("equals","cross","modulo","bucket");
    private static final java.util.Set<String> GUARDS=java.util.Set.of("prey_present","prey_over_required","prey_at_least_required");

    /** Per-evaluation context handed to the named meter effects/actions/guards. */
    private record BuildupContext(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player,
            WashedAshoreProgress progress,java.util.List<Animal> prey,EncounterAnchor encounter){}
    @FunctionalInterface private interface MeterEffect { void run(BuildupContext ctx,ActivationProfile profile); }

    // Named milestone effects a data-defined activation may reference.
    private static final java.util.Map<String,MeterEffect> EFFECTS=java.util.Map.of(
            "howl",(ctx,profile)->howl(ctx.level(),ctx.player()),
            "darken",(ctx,profile)->darkenPocket(ctx.level(),ctx.player()),
            "silence_prey",(ctx,profile)->{ if(!ctx.prey().isEmpty())randomPrey(ctx).setSilent(true); },
            "remove_prey",(ctx,profile)->{ if(!ctx.prey().isEmpty())randomPrey(ctx).discard(); },
            "kill_prey",(ctx,profile)->{ if(!ctx.prey().isEmpty())randomPrey(ctx).hurt(ctx.level().damageSources().magic(),Float.MAX_VALUE); });
    // Named completion actions (boss-spawn / manifestation glue).
    private static final java.util.Map<String,MeterEffect> ACTIONS=java.util.Map.of(
            "begin_manifestation",RegionalQuestManager::beginManifestation,
            "raise_crossing_horde",RegionalQuestManager::raiseCrossingHorde);

    public static java.util.Set<String> meterEffects(){return EFFECTS.keySet();}
    public static java.util.Set<String> meterActions(){return ACTIONS.keySet();}
    public static java.util.Set<String> meterGuards(){return GUARDS;}
    public static java.util.Set<String> milestoneModes(){return MILESTONE_MODES;}

    private RegionalQuestManager(){}

    private static Animal randomPrey(BuildupContext ctx){return ctx.prey().get(ctx.level().random.nextInt(ctx.prey().size()));}
    private static ActivationProfile profile(ResourceLocation encounterId){
        return EncounterManager.definitions().get(encounterId).map(EncounterDefinition::activation).orElse(null);
    }
    /** Fires every data-defined milestone whose trigger matches the meter moving {@code before}->{@code now}. */
    private static void walkMilestones(ActivationProfile profile,BuildupContext ctx,int before,int now){
        for(ActivationMilestone m:profile.milestones()){
            boolean fire=switch(m.mode()){
                case "equals"->now==m.at();
                case "cross"->before<m.at()&&now>=m.at();
                case "modulo"->m.step()>0&&now>=m.at()&&now%m.step()==0;
                case "bucket"->m.step()>0&&now/m.step()>before/m.step()&&now<profile.threshold();
                default->false;
            };
            if(!fire)continue;
            if(m.guard()!=null&&!guardPasses(m.guard(),profile,ctx))continue;
            if(m.effect()!=null){MeterEffect effect=EFFECTS.get(m.effect());if(effect!=null)effect.run(ctx,profile);}
            if(m.message()!=null){
                if(m.passLevel())CampaignMessages.send(ctx.player(),m.message(),now);
                else CampaignMessages.send(ctx.player(),m.message());
            }
        }
    }
    private static void tryComplete(ActivationProfile profile,BuildupContext ctx){
        ActivationCompletion completion=profile.onFull();
        if(completion==null)return;
        if(completion.guard()!=null&&!guardPasses(completion.guard(),profile,ctx))return;
        if(completion.message()!=null)CampaignMessages.send(ctx.player(),completion.message());
        MeterEffect action=ACTIONS.get(completion.action());
        if(action!=null)action.run(ctx,profile);
    }
    private static boolean guardPasses(String guard,ActivationProfile profile,BuildupContext ctx){
        int count=ctx.prey().size();
        return switch(guard){
            case "prey_present"->count>0;
            case "prey_over_required"->count>profile.nearbyPrey();
            case "prey_at_least_required"->count>=profile.nearbyPrey();
            default->false;
        };
    }
    private static void beginManifestation(BuildupContext ctx,ActivationProfile profile){
        ctx.progress().setDreadQuest(RegionalQuestStage.MANIFESTING);
        CampaignCore.LOGGER.info("dread_manifestation_started player={} prey={} biome={}",ctx.player().getUUID(),ctx.prey().size(),
                ctx.level().getBiome(ctx.player().blockPosition()).unwrapKey().map(Object::toString).orElse("unknown"));
    }
    private static void raiseCrossingHorde(BuildupContext ctx,ActivationProfile profile){
        // The authored Thrasher boss is unfinished, so the completed investigation instead raises a
        // temporary wave horde from the churned ground around the crossing (see CrossingHordeManager).
        ctx.progress().setCrossingQuest(RegionalQuestStage.BOSS_ACTIVE);
        CrossingHordeManager.begin(ctx.level(),ctx.data(),ctx.encounter(),ctx.player());
        CampaignCore.LOGGER.info("devils_crossing_investigation_complete player={} crossing={}",
                ctx.player().getUUID(),ctx.data().act().devilsCrossing());
    }
    /** A surface column a short distance from the player, in a random horizontal direction. */
    private static BlockPos thrasherSpawnNear(ServerLevel level,ServerPlayer player){
        double angle=level.random.nextDouble()*Math.PI*2;
        return surface(level,player.blockPosition().offset(
                Mth.floor(Math.cos(angle)*THRASHER_SPAWN_DISTANCE),0,Mth.floor(Math.sin(angle)*THRASHER_SPAWN_DISTANCE)));
    }
    private static boolean inBiome(ServerLevel level,ServerPlayer player,ActivationProfile profile){
        if(profile.biomeTag().isEmpty())return true;
        TagKey<Biome> tag=TagKey.create(Registries.BIOME,profile.biomeTag().get());
        return level.getBiome(player.blockPosition()).is(tag);
    }
    private static double square(double value){return value*value;}

    public static void tick(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player){
        WashedAshoreProgress progress=data.player(player.getUUID());
        boolean normalAccess=progress.stage().atLeast(WashedAshoreStage.REGIONAL_OBJECTIVES);
        boolean dreadFragment=holds(player,CampaignItems.DREAD_FRAGMENT.get());
        boolean writhingFragment=holds(player,CampaignItems.WRITHING_FRAGMENT.get());
        if(!normalAccess&&!dreadFragment&&!writhingFragment)return;
        if(normalAccess)unlock(progress,player,data);
        if(normalAccess||dreadFragment)tickDread(level,data,player,progress,dreadFragment&&!normalAccess);
        if(normalAccess||writhingFragment)tickCrossing(level,data,player,progress,writhingFragment&&!normalAccess);
    }
    private static void unlock(WashedAshoreProgress progress,ServerPlayer player,WashedAshoreSavedData data){
        boolean changed=false;
        if(progress.dreadQuest()==RegionalQuestStage.LOCKED){
            progress.setDreadQuest(RegionalQuestStage.AVAILABLE);
            CampaignMessages.send(player,"dark_forest");changed=true;
        }
        if(progress.crossingQuest()==RegionalQuestStage.LOCKED){
            progress.setCrossingQuest(RegionalQuestStage.AVAILABLE);
            CampaignMessages.send(player,"crossing_route",
                    Component.translatable("direction.campaign_core.washed_ashore."+directionHint(player.blockPosition(),data.act().devilsCrossing())));
            // Regional Encounter C is its own objective now: warn the distant settlement before the raid.
            if(!data.act().completedWorldObjectives().contains(EncounterManager.REGIONAL_C))
                CampaignMessages.send(player,"other_settlement",
                        SettlementDialogueNames.distant(player.serverLevel(),data.act()),
                        Component.translatable("direction.campaign_core.washed_ashore."+directionHint(player.blockPosition(),data.act().otherSettlement())));
            changed=true;
        }
        if(changed)data.dirty();
    }
    private static void tickDread(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player,WashedAshoreProgress progress,
                                  boolean fragmentAccess){
        WashedAshoreInstance instance=nearestInstance(data,player,"dark_forest");
        EncounterAnchor encounter=instance==null?null:instance.encounters().get(EncounterManager.CONSUMING_DREAD);
        if(encounter==null||encounter.status()!=EncounterStatus.DORMANT||progress.dreadQuest()==RegionalQuestStage.COMPLETE)return;
        if(progress.dreadQuest()==RegionalQuestStage.MANIFESTING){
            tickDreadManifestation(level,data,player,progress,encounter);return;
        }
        ActivationProfile profile=profile(EncounterManager.CONSUMING_DREAD);
        if(profile==null)return;
        maybeSeedDreadPrey(level,data,instance,player,encounter,profile);
        if(!inBiome(level,player,profile)){
            if(progress.dreadLevel()>0)progress.changeDreadLevel(profile.fillDown());
            return;
        }
        AABB search=player.getBoundingBox().inflate(profile.radius(),PREY_SEARCH_HEIGHT,profile.radius());
        java.util.List<Animal> preyMobs=level.getEntitiesOfClass(Animal.class,search,a->a instanceof Pig||a instanceof Sheep||a instanceof Cow);
        int before=progress.dreadLevel();
        int now=progress.changeDreadLevel(preyMobs.size()>=profile.nearbyPrey()?profile.fillUp():profile.fillDown());
        if(fragmentAccess&&now>before)progress.beginFragmentDreadInvocation();
        BuildupContext ctx=new BuildupContext(level,data,player,progress,preyMobs,encounter);
        walkMilestones(profile,ctx,before,now);
        if(now>=profile.threshold())tryComplete(profile,ctx);
        data.dirty();
    }
    /**
     * When a player first approaches the dread POI, stock the grove with a flock of sheep and a herd
     * of cattle (~4-6 each) so a dread event has enough prey to feed on. No-ops once the grove has been
     * seeded, or if the forest already holds enough livestock for the event to build.
     */
    private static void maybeSeedDreadPrey(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance instance,ServerPlayer player,
                                           EncounterAnchor encounter,ActivationProfile profile){
        if(instance.completedWorldObjectives().contains(DREAD_PREY_SEEDED))return;
        BlockPos anchor=encounter.anchorPos();
        if(player.blockPosition().distSqr(anchor)>square(encounter.activationRadius()))return;
        AABB near=new AABB(anchor).inflate(profile.radius(),PREY_SEARCH_HEIGHT,profile.radius());
        int existing=level.getEntitiesOfClass(Animal.class,near,a->a instanceof Pig||a instanceof Sheep||a instanceof Cow).size();
        if(existing>=profile.nearbyPrey())return;
        int sheep=spawnHerd(level,anchor,EntityType.SHEEP,4+level.random.nextInt(3));
        int cattle=spawnHerd(level,anchor,EntityType.COW,4+level.random.nextInt(3));
        instance.completedWorldObjectives().add(DREAD_PREY_SEEDED);
        data.dirty();
        CampaignCore.LOGGER.info("dread_prey_seeded anchor={} existing={} sheep={} cattle={} player={}",
                anchor,existing,sheep,cattle,player.getUUID());
    }
    private static int spawnHerd(ServerLevel level,BlockPos center,EntityType<? extends Animal> type,int count){
        int spawned=0;
        for(int i=0;i<count;i++){
            double angle=level.random.nextDouble()*Math.PI*2,distance=3+level.random.nextDouble()*(DREAD_PREY_SPAWN_RADIUS-3);
            BlockPos ground=surface(level,center.offset(Mth.floor(Math.cos(angle)*distance),0,Mth.floor(Math.sin(angle)*distance)));
            Animal animal=type.create(level);
            if(animal==null)continue;
            animal.moveTo(ground.getX()+.5,ground.getY()+1,ground.getZ()+.5,level.random.nextFloat()*360,0);
            animal.finalizeSpawn(level,level.getCurrentDifficultyAt(ground),MobSpawnType.NATURAL,null);
            if(level.addFreshEntity(animal))spawned++;
        }
        return spawned;
    }
    private static void tickDreadManifestation(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player,
                                                WashedAshoreProgress progress,EncounterAnchor encounter){
        int ticks=progress.advanceDreadManifest(10);
        double angle=ticks*.12;
        Vec3 center=player.position().add(Math.cos(angle)*10,1.1,Math.sin(angle)*10);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,center.x,center.y,center.z,18,1.4,.8,1.4,.015);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,center.x,center.y+.5,center.z,2,.3,.15,.3,0);
        if(ticks%40==0)howl(level,player);
        if(ticks<120){data.dirty();return;}
        BlockPos spawn=surface(level,BlockPos.containing(center));
        encounter.relocate(spawn,spawn.above());
        progress.setDreadQuest(RegionalQuestStage.BOSS_ACTIVE);
        EncounterManager.activate(level,data,encounter,player,!progress.fragmentDreadInvocation());
        data.dirty();
        CampaignCore.LOGGER.info("dread_fully_emerged player={} spawn={}",player.getUUID(),spawn);
    }
    private static void howl(ServerLevel level,ServerPlayer player){
        BlockPos p=player.blockPosition().offset(level.random.nextInt(41)-20,level.random.nextInt(7)-3,level.random.nextInt(41)-20);
        level.playSound(null,p,SoundEvents.WOLF_AMBIENT,SoundSource.HOSTILE,2.5f,.55f+level.random.nextFloat()*.2f);
    }
    private static void darkenPocket(ServerLevel level,ServerPlayer player){
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,70,0,false,false));
        Vec3 p=player.position().add(level.random.nextDouble()*16-8,1,level.random.nextDouble()*16-8);
        level.sendParticles(ParticleTypes.SQUID_INK,p.x,p.y,p.z,22,2.5,1.5,2.5,.01);
    }
    public static void onLivestockDeath(ServerLevel level,Entity entity,DamageSource source){
        if(!(entity instanceof Pig||entity instanceof Sheep||entity instanceof Cow)
                ||!level.getBiome(entity.blockPosition()).is(BiomeTags.IS_FOREST))return;
        WashedAshoreSavedData data=WashedAshoreSavedData.get(level);
        EncounterAnchor encounter=data.act().encounters().get(EncounterManager.CONSUMING_DREAD);
        if(encounter==null||encounter.status()!=EncounterStatus.DORMANT)return;
        boolean changed=false;
        for(ServerPlayer player:level.players()){
            if(player.distanceToSqr(entity)>48*48)continue;
            WashedAshoreProgress progress=data.player(player.getUUID());
            boolean normalAccess=progress.stage().atLeast(WashedAshoreStage.REGIONAL_OBJECTIVES)
                    &&progress.dreadQuest()!=RegionalQuestStage.LOCKED;
            boolean fragmentAccess=holds(player,CampaignItems.DREAD_FRAGMENT.get());
            if(!normalAccess&&!fragmentAccess
                    ||progress.dreadQuest()==RegionalQuestStage.COMPLETE
                    ||progress.dreadQuest()==RegionalQuestStage.BOSS_ACTIVE
                    ||progress.dreadQuest()==RegionalQuestStage.MANIFESTING)continue;
            int before=progress.dreadLevel();
            int now=progress.changeDreadLevel(LIVESTOCK_DEATH_DREAD);
            if(fragmentAccess&&!normalAccess)progress.beginFragmentDreadInvocation();
            CampaignMessages.send(player,"dread_feeds_on_death");
            howl(level,player);
            if(before<45&&now>=45)darkenPocket(level,player);
            changed=true;
            CampaignCore.LOGGER.info("dread_livestock_death player={} animal={} source={} before={} after={}",
                    player.getUUID(),BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                    source.getMsgId(),before,now);
        }
        if(changed)data.dirty();
    }
    /**
     * Returns the regional quest tied to a retried encounter to its available state for every player,
     * clearing any buildup so it can be picked up again. Completed quests are left untouched. Encounters
     * without an associated regional quest (e.g. the undertaker) simply reopen via their DORMANT state.
     */
    public static void reopenEncounterQuest(ServerLevel level,WashedAshoreSavedData data,ResourceLocation encounterId){
        boolean dread=encounterId.equals(EncounterManager.CONSUMING_DREAD);
        boolean crossing=encounterId.equals(EncounterManager.THRASHER);
        if(!dread&&!crossing)return;
        // Wipe the world-level horde state so a retried crossing rises again from its first wave.
        if(crossing)CrossingHordeManager.reset(data);
        for(ServerPlayer player:level.players()){
            WashedAshoreProgress progress=data.player(player.getUUID());
            if(dread&&progress.dreadQuest()!=RegionalQuestStage.COMPLETE){
                // Drop to LOCKED so unlock() re-announces and returns it to AVAILABLE next tick.
                progress.setDreadQuest(RegionalQuestStage.LOCKED);
                progress.setDreadLevel(0);progress.resetDreadManifest();progress.clearFragmentDreadInvocation();
            }
            if(crossing&&progress.crossingQuest()!=RegionalQuestStage.COMPLETE){
                progress.setCrossingQuest(RegionalQuestStage.LOCKED);
                progress.setCrossingInvestigation(0);progress.clearFragmentCrossingInvocation();
            }
        }
        data.dirty();
        CampaignCore.LOGGER.info("regional_quest_reopened encounter={} players={}",encounterId,level.players().size());
    }
    public static boolean debugEvent(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player,String event){
        WashedAshoreProgress progress=data.player(player.getUUID());
        AABB search=player.getBoundingBox().inflate(32,12,32);
        java.util.List<Animal> prey=level.getEntitiesOfClass(Animal.class,search,a->a instanceof Pig||a instanceof Sheep||a instanceof Cow);
        switch(event){
            case "howl" -> howl(level,player);
            case "darkness" -> darkenPocket(level,player);
            case "silence" -> {
                if(prey.isEmpty())return false;
                prey.get(level.random.nextInt(prey.size())).setSilent(true);
                CampaignMessages.send(player,"forest_quiet");
            }
            case "missing" -> {
                if(prey.isEmpty())return false;
                prey.get(level.random.nextInt(prey.size())).discard();
                CampaignMessages.send(player,"prey_missing");
            }
            case "manifest" -> {
                progress.setDreadLevel(100);progress.resetDreadManifest();
                progress.setDreadQuest(RegionalQuestStage.MANIFESTING);
                CampaignMessages.send(player,"dread_manifest");
            }
            case "spawn_dread" -> {
                EncounterAnchor encounter=data.act().encounters().get(EncounterManager.CONSUMING_DREAD);
                if(encounter==null)return false;
                BlockPos spawn=surface(level,player.blockPosition().offset(8,0,0));
                encounter.relocate(spawn,spawn.above());progress.setDreadQuest(RegionalQuestStage.BOSS_ACTIVE);
                if(!EncounterManager.activate(level,data,encounter,player))return false;
            }
            case "discover_crossing" -> {
                progress.setCrossingQuest(RegionalQuestStage.CLUE_FOUND);
                progress.discover(CampaignCore.washedAshoreId("devils_crossing"));
                CampaignMessages.send(player,"crossing_found");
            }
            case "spawn_thrasher" -> {
                EncounterAnchor encounter=data.act().encounters().get(EncounterManager.THRASHER);
                if(encounter==null)return false;
                BlockPos spawn=thrasherSpawnNear(level,player);
                encounter.relocate(spawn,spawn.above());progress.setCrossingQuest(RegionalQuestStage.BOSS_ACTIVE);
                if(!EncounterManager.activate(level,data,encounter,player))return false;
            }
            case "spawn_crossing_horde" -> {
                EncounterAnchor encounter=data.act().encounters().get(EncounterManager.THRASHER);
                if(encounter==null)return false;
                CrossingHordeManager.reset(data);
                progress.setCrossingQuest(RegionalQuestStage.BOSS_ACTIVE);
                CrossingHordeManager.begin(level,data,encounter,player);
            }
            case "form_sculk" -> {if(!SculkSurfaceManager.debugForm(level,data,player))return false;}
            case "start_sculk" -> {if(!SculkSurfaceManager.debugStart(level,data,player))return false;}
            case "reset_sculk" -> SculkSurfaceManager.reset(level,data);
            default -> {return false;}
        }
        data.dirty();
        CampaignCore.LOGGER.info("quest_debug_event event={} player={}",event,player.getUUID());
        return true;
    }
    private static void tickCrossing(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player,WashedAshoreProgress progress,
                                     boolean fragmentAccess){
        WashedAshoreInstance instance=nearestInstance(data,player,"devils_crossing");
        if(instance==null||!WashedAshoreLayoutGenerator.isDevilsCrossingPlaced(instance))return;
        BlockPos crossing=instance.devilsCrossing();
        EncounterAnchor encounter=instance.encounters().get(EncounterManager.THRASHER);
        if(crossing==null||encounter==null||encounter.status()!=EncounterStatus.DORMANT||progress.crossingQuest()==RegionalQuestStage.COMPLETE)return;
        ActivationProfile profile=profile(EncounterManager.THRASHER);
        if(profile==null)return;
        double distance=player.blockPosition().distSqr(crossing);
        if((progress.crossingQuest()==RegionalQuestStage.AVAILABLE
                ||fragmentAccess&&progress.crossingQuest()==RegionalQuestStage.LOCKED)
                &&distance<=square(profile.discoveryRadius())){
            if(fragmentAccess&&progress.crossingQuest()==RegionalQuestStage.LOCKED)
                progress.beginFragmentCrossingInvocation();
            progress.setCrossingQuest(RegionalQuestStage.CLUE_FOUND);
            progress.discover(CampaignCore.washedAshoreId("devils_crossing"));
            CampaignMessages.send(player,"crossing_found");
            CampaignMessages.send(player,"investigate_crossing");
            data.dirty();
        }
        if(!progress.crossingQuest().equals(RegionalQuestStage.CLUE_FOUND)&&!progress.crossingQuest().equals(RegionalQuestStage.INVESTIGATING))return;
        if(distance>square(profile.radius()))return;
        boolean lookingDown=player.getLookAngle().y<LOOK_DOWN_THRESHOLD;
        int before=progress.crossingInvestigation();
        int now=progress.investigateCrossing(lookingDown?profile.fillUp():profile.fillDown());
        if(lookingDown)progress.setCrossingQuest(RegionalQuestStage.INVESTIGATING);
        BuildupContext ctx=new BuildupContext(level,data,player,progress,java.util.List.of(),encounter);
        walkMilestones(profile,ctx,before,now);
        if(now>=profile.threshold())tryComplete(profile,ctx);
        data.dirty();
    }
    private static boolean holds(ServerPlayer player,net.minecraft.world.item.Item item){
        return player.getMainHandItem().is(item)||player.getOffhandItem().is(item);
    }
    private static WashedAshoreInstance nearestInstance(WashedAshoreSavedData data,ServerPlayer player,String slot){
        WashedAshoreInstance nearest=null;double distance=Double.MAX_VALUE;
        for(WashedAshoreInstance instance:data.instances()){BlockPos pos=instance.slot(slot);if(pos==null)continue;double d=player.blockPosition().distSqr(pos);if(d<distance){distance=d;nearest=instance;}}
        return nearest;
    }
    private static BlockPos surface(ServerLevel level,BlockPos pos){
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,new BlockPos(pos.getX(),0,pos.getZ())).below();
    }
    private static String directionHint(BlockPos from,BlockPos to){
        if(to==null)return "beyond";
        int dx=to.getX()-from.getX(),dz=to.getZ()-from.getZ();
        String northSouth=Math.abs(dz)<40?"":(dz<0?"north":"south");
        String eastWest=Math.abs(dx)<40?"":(dx<0?"west":"east");
        String result=northSouth.isEmpty()?eastWest:eastWest.isEmpty()?northSouth:northSouth+"_"+eastWest;
        return result.isEmpty()?"nearby":result;
    }
}
