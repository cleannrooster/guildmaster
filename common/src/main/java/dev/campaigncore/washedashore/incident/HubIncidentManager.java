package dev.campaigncore.washedashore.incident;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.washedashore.act.WashedAshoreInstance;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import dev.campaigncore.washedashore.encounter.CampaignSpawnProtection;
import dev.campaigncore.washedashore.message.SettlementDialogueNames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** World-level scheduler and runtime for data-defined hub incidents. */
public final class HubIncidentManager {
    public static final String MEMBER_TAG="campaign_core_hub_incident";
    private static final String PROTECTED_TAG="campaign_core_hub_incident_protected",OPPONENT_TAG="campaign_core_hub_incident_opponent";
    private static final int TICK_INTERVAL=20;
    private HubIncidentManager(){}

    public static void tick(ServerLevel level,WashedAshoreSavedData data){
        if(level!=level.getServer().overworld()||level.getGameTime()%TICK_INTERVAL!=0)return;
        long now=level.getGameTime();
        for(WashedAshoreInstance act:data.instances())if(act.contentReady())for(var entry:HubIncidentRegistry.hubs().entrySet()){
            ResourceLocation hubId=entry.getKey();HubDefinition hub=entry.getValue();BlockPos hubCenter=act.slot(hub.slot());
            if(hubCenter==null)continue;
            HubIncidentState state=data.hubIncident(act.actInstanceId(),hubId);
            cleanupStale(level,state.center()==null?hubCenter:state.center(),state);
            if(state.active())tickActive(level,data,hub,state,now);
            else if(state.nextSelectionAt()==0){state.setNextSelectionAt(now+hub.intervalTicks());data.dirty();}
            else if(now>=state.nextSelectionAt()&&hasNearbyPlayer(level,hubCenter,hub.playerRadius()))start(level,data,act,hubId,hub,state,hubCenter,now);
        }
    }

    private static void start(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act,ResourceLocation hubId,HubDefinition hub,
                              HubIncidentState state,BlockPos hubCenter,long now){
        List<Map.Entry<ResourceLocation,HubIncidentDefinition>> choices=eligible(hub,state,false);
        if(choices.isEmpty())choices=eligible(hub,state,true);
        if(choices.isEmpty()){state.setNextSelectionAt(now+hub.intervalTicks());data.dirty();return;}
        int total=choices.stream().mapToInt(e->e.getValue().weight()).sum(),roll=level.random.nextInt(total);
        Map.Entry<ResourceLocation,HubIncidentDefinition> selected=choices.getFirst();
        for(var choice:choices)if((roll-=choice.getValue().weight())<0){selected=choice;break;}
        HubIncidentDefinition definition=selected.getValue();
        BlockPos center=definition.special().locationSlot().map(act::slot).orElse(hubCenter);
        if(center==null){state.setNextSelectionAt(now+20*60);data.dirty();return;}
        BlockPos destination=patrolDestination(level,center,definition);
        List<UUID> spawned=spawnRoster(level,selected.getKey(),definition,center,destination,false);
        if(spawned.isEmpty()){state.setNextSelectionAt(now+20*60);data.dirty();return;}
        state.begin(selected.getKey(),now,now+definition.durationTicks(),center,destination,spawned,spawned.getFirst());
        prepareProtected(level,state,selected.getKey(),definition,center);
        state.opponents().addAll(spawnRoster(level,selected.getKey(),definition,center,destination,true));
        data.dirty();announce(level,hubCenter,hub.playerRadius(),selected.getKey(),"start");
        CampaignCore.LOGGER.info("hub_incident_started hub={} incident={} members={} protected={} opponents={}",hubId,selected.getKey(),spawned.size(),state.protectedEntities().size(),state.opponents().size());
    }

    private static List<Map.Entry<ResourceLocation,HubIncidentDefinition>> eligible(HubDefinition hub,HubIncidentState state,boolean allowRepeat){
        List<Map.Entry<ResourceLocation,HubIncidentDefinition>> choices=new ArrayList<>();
        for(var entry:HubIncidentRegistry.incidents().entrySet())if(entry.getValue().tier()==hub.tier()
                &&(hub.incidents().isEmpty()||hub.incidents().contains(entry.getKey()))
                &&(allowRepeat||!entry.getKey().equals(state.lastIncident())))choices.add(entry);
        return choices;
    }

    private static void prepareProtected(ServerLevel level,HubIncidentState state,ResourceLocation incidentId,HubIncidentDefinition definition,BlockPos center){
        HubIncidentSpecial special=definition.special();if(special.protectedEntity().isEmpty()||special.protectedCount()==0)return;
        ResourceLocation id=special.protectedEntity().get();
        if(special.useNearbyProtected()){
            for(LivingEntity entity:level.getEntitiesOfClass(LivingEntity.class,new AABB(center).inflate(definition.maximumDistance()),
                    e->BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(id)))state.protectedEntities().add(entity.getUUID());
            if(!state.protectedEntities().isEmpty())return;
        }
        EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(id);if(!resolves(type,id))return;
        for(int i=0;i<special.protectedCount();i++){
            Entity entity=type.create(level);if(entity==null)continue;CampaignSpawnProtection.preventRpgMinibossRecruitment(entity);BlockPos pos=surface(level,center.offset(level.random.nextInt(9)-4,0,level.random.nextInt(9)-4));
            entity.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,level.random.nextFloat()*360,0);entity.addTag(MEMBER_TAG);entity.addTag(MEMBER_TAG+"="+incidentId);entity.addTag(PROTECTED_TAG);
            if(entity instanceof Mob mob){mob.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),MobSpawnType.EVENT,null);mob.setPersistenceRequired();}
            if(level.addFreshEntity(entity)){state.protectedEntities().add(entity.getUUID());state.temporaryEntities().add(entity.getUUID());}
        }
    }

    private static List<UUID> spawnRoster(ServerLevel level,ResourceLocation incidentId,HubIncidentDefinition definition,
                                           BlockPos center,BlockPos destination,boolean opponents){
        HubIncidentSpecial special=definition.special();List<ResourceLocation> roster=opponents?special.opponents():definition.entities();
        int count=opponents?special.opponentCount():definition.count();List<UUID> spawned=new ArrayList<>();
        for(int i=0;i<count;i++){
            ResourceLocation entityId=roster.get(i%roster.size());EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(entityId);
            if(!resolves(type,entityId))continue;Entity entity=type.create(level);if(entity==null)continue;
            CampaignSpawnProtection.protectFromSun(entity);entity.addTag(MEMBER_TAG);entity.addTag(MEMBER_TAG+"="+incidentId);
            if(opponents)entity.addTag(OPPONENT_TAG);
            BlockPos spawn=spawnPosition(level,center,definition,i,opponents);entity.moveTo(spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5,level.random.nextFloat()*360,0);
            if(entity instanceof Mob mob){
                mob.finalizeSpawn(level,level.getCurrentDifficultyAt(spawn),MobSpawnType.EVENT,null);mob.setPersistenceRequired();
                (opponents?special.opponentAttributes():special.attributes()).applyTo(mob);
            }
            if(!opponents&&special.mount().isPresent()&&entity instanceof LivingEntity rider){
                ResourceLocation mountId=special.mount().get();EntityType<?> mountType=BuiltInRegistries.ENTITY_TYPE.get(mountId);Entity mount=resolves(mountType,mountId)?mountType.create(level):null;
                if(mount!=null){CampaignSpawnProtection.preventRpgMinibossRecruitment(mount);mount.moveTo(entity.getX(),entity.getY(),entity.getZ(),entity.getYRot(),0);mount.addTag(MEMBER_TAG);mount.addTag(MEMBER_TAG+"="+incidentId);if(level.addFreshEntity(mount))rider.startRiding(mount,true);}
                if(entity instanceof Zombie zombie)zombie.setBaby(true);
            }
            if(level.addFreshEntity(entity)){
                spawned.add(entity.getUUID());
                // Path creation can fail while an entity is not yet part of the level. Start its
                // incident movement only after addFreshEntity succeeds.
                if(entity instanceof Mob mob)navigate(mob,definition.spawn(),center,destination);
            }
        }
        return spawned;
    }

    private static void tickActive(ServerLevel level,WashedAshoreSavedData data,HubDefinition hub,HubIncidentState state,long now){
        HubIncidentDefinition definition=HubIncidentRegistry.incident(state.activeIncident()).orElse(null);
        if(definition==null){finish(level,data,hub,state,now,false);return;}
        removeKnownDead(level,state.members());removeKnownDead(level,state.protectedEntities());removeKnownDead(level,state.opponents());
        HubIncidentSpecial special=definition.special();
        if(special.minimumSurvivors()>0&&state.protectedEntities().size()<special.minimumSurvivors()){finish(level,data,hub,state,now,false);return;}
        if(state.members().isEmpty()&&state.wave()<special.waves()){
            if(state.nextWaveAt()==0)state.setNextWaveAt(now+special.waveIntervalTicks());
            else if(now>=state.nextWaveAt()){
                List<UUID> wave=spawnRoster(level,state.activeIncident(),definition,state.center(),state.destination(),false);
                if(!wave.isEmpty())state.addMembers(wave);
            }
        }
        boolean allWaves=state.wave()>=special.waves();
        if(definition.objective()==HubIncidentObjectiveType.KILL_GROUP&&allWaves&&state.members().isEmpty()){finish(level,data,hub,state,now,true);return;}
        if(definition.objective()==HubIncidentObjectiveType.KILL_LEADER&&state.leader()!=null&&!state.members().contains(state.leader())){finish(level,data,hub,state,now,true);return;}
        if(definition.objective()==HubIncidentObjectiveType.BATTLE&&(state.members().isEmpty()||state.opponents().isEmpty())){finish(level,data,hub,state,now,true);return;}
        if(definition.objective()==HubIncidentObjectiveType.DEFEND_LOCATION){
            for(UUID id:state.members()){Entity entity=level.getEntity(id);if(entity!=null&&entity.blockPosition().distSqr(state.center())<=Mth.square(definition.defenseRadius())){finish(level,data,hub,state,now,false);return;}}
            if(now>=state.expiresAt()){finish(level,data,hub,state,now,true);return;}
        }else if(now>=state.expiresAt()){finish(level,data,hub,state,now,false);return;}
        retarget(level,state,definition);data.dirty();
    }

    private static void retarget(ServerLevel level,HubIncidentState state,HubIncidentDefinition definition){
        for(UUID id:state.members())if(level.getEntity(id) instanceof Mob mob){
            LivingEntity target=nearest(level,mob,state.protectedEntities());
            if(target==null&&definition.objective()==HubIncidentObjectiveType.BATTLE)target=nearest(level,mob,state.opponents());
            if(target!=null)mob.setTarget(target);else navigate(mob,definition.spawn(),state.center(),state.destination());
        }
        for(UUID id:state.opponents())if(level.getEntity(id) instanceof Mob mob){LivingEntity target=nearest(level,mob,state.members());if(target!=null)mob.setTarget(target);}
    }
    private static LivingEntity nearest(ServerLevel level,Entity source,Collection<UUID> ids){LivingEntity best=null;double distance=Double.MAX_VALUE;for(UUID id:ids){Entity e=level.getEntity(id);if(e instanceof LivingEntity living&&living.isAlive()){double d=source.distanceToSqr(e);if(d<distance){distance=d;best=living;}}}return best;}

    private static void finish(ServerLevel level,WashedAshoreSavedData data,HubDefinition hub,HubIncidentState state,long now,boolean success){
        Set<UUID> cleanup=new HashSet<>(state.members());cleanup.addAll(state.opponents());cleanup.addAll(state.temporaryEntities());
        for(UUID id:cleanup){Entity entity=level.getEntity(id);if(entity!=null){if(entity.getVehicle()!=null)entity.getVehicle().discard();entity.discard();}}
        ResourceLocation incident=state.activeIncident();BlockPos center=state.center();
        if(success&&center!=null)HubIncidentRegistry.incident(incident).flatMap(HubIncidentDefinition::rewardLootTable)
                .ifPresent(table->grantResponderRewards(level,center,hub.playerRadius(),incident,table));
        state.finish(now+hub.intervalTicks());data.dirty();
        announce(level,center,hub.playerRadius(),incident,success?"success":"failure");CampaignCore.LOGGER.info("hub_incident_finished incident={} success={}",incident,success);
    }
    private static void grantResponderRewards(ServerLevel level,BlockPos center,int radius,ResourceLocation incidentId,ResourceLocation tableId){
        var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,tableId);
        var table=level.getServer().reloadableRegistries().getLootTable(key);
        for(ServerPlayer player:level.players()){
            if(!player.isAlive()||player.blockPosition().distSqr(center)>Mth.square(radius))continue;
            var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,player.position())
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
            for(var stack:table.getRandomItems(params))if(!stack.isEmpty()&&!player.getInventory().add(stack))player.drop(stack,false);
            CampaignCore.LOGGER.info("hub_incident_reward_granted incident={} player={} table={}",incidentId,player.getUUID(),tableId);
        }
    }
    private static void navigate(Mob mob,HubIncidentSpawnType type,BlockPos center,BlockPos destination){
        if(type==HubIncidentSpawnType.APPROACHING_HUB)mob.getNavigation().moveTo(center.getX()+.5,center.getY(),center.getZ()+.5,1.0);
        else if(type==HubIncidentSpawnType.MOVING_PATROL&&destination!=null)mob.getNavigation().moveTo(destination.getX()+.5,destination.getY(),destination.getZ()+.5,0.8);
    }
    private static BlockPos spawnPosition(ServerLevel level,BlockPos center,HubIncidentDefinition definition,int index,boolean opponent){
        double angle=(definition.spawn()==HubIncidentSpawnType.MOVING_PATROL?level.random.nextDouble()*Math.PI*2:(Math.PI*2*index/Math.max(1,definition.count()))+level.random.nextDouble()*.4)+(opponent?Math.PI:0);
        double distance=definition.minimumDistance()+level.random.nextDouble()*(definition.maximumDistance()-definition.minimumDistance());
        return surface(level,center.offset(Mth.floor(Math.cos(angle)*distance),0,Mth.floor(Math.sin(angle)*distance)));
    }
    private static BlockPos patrolDestination(ServerLevel level,BlockPos center,HubIncidentDefinition definition){
        if(definition.spawn()!=HubIncidentSpawnType.MOVING_PATROL)return center;double angle=level.random.nextDouble()*Math.PI*2;int distance=definition.maximumDistance();
        return surface(level,center.offset(Mth.floor(Math.cos(angle)*distance),0,Mth.floor(Math.sin(angle)*distance)));
    }
    private static BlockPos surface(ServerLevel level,BlockPos pos){return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos);}
    private static boolean resolves(EntityType<?> type,ResourceLocation id){return type!=null&&(type!=EntityType.PIG||id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG)));}
    private static void removeKnownDead(ServerLevel level,Collection<UUID> ids){ids.removeIf(id->{Entity entity=level.getEntity(id);return entity!=null&&!entity.isAlive();});}
    private static void cleanupStale(ServerLevel level,BlockPos center,HubIncidentState state){
        String active=state.active()?MEMBER_TAG+"="+state.activeIncident():null;
        for(Entity entity:level.getEntitiesOfClass(Entity.class,new AABB(center).inflate(160),e->e.getTags().stream().anyMatch(t->t.startsWith(MEMBER_TAG+"="))))
            if(active==null||!entity.getTags().contains(active))entity.discard();
    }
    private static boolean hasNearbyPlayer(ServerLevel level,BlockPos center,int radius){return level.players().stream().anyMatch(p->p.isAlive()&&p.blockPosition().distSqr(center)<=Mth.square(radius));}
    private static void announce(ServerLevel level,BlockPos center,int radius,ResourceLocation incident,String phase){
        if(center==null)return;
        HubIncidentDefinition definition=HubIncidentRegistry.incident(incident).orElse(null);
        String fallback=definition!=null&&definition.tier()==1?"settlement.campaign_core.washed_ashore.primary"
                :definition!=null&&definition.tier()==3?"settlement.campaign_core.washed_ashore.secondary"
                :"settlement.campaign_core.washed_ashore.devils_crossing";
        Component settlementName=SettlementDialogueNames.nearest(level,center,fallback);
        Component message=Component.translatable("message.campaign_core.hub_incident."+incident.getPath().replace('/','.')+"."+phase,settlementName);
        for(var player:level.players())if(player.blockPosition().distSqr(center)<=Mth.square(radius))player.sendSystemMessage(message);
    }

    /** Operator/debug entry point: starts a specific definition immediately at a compatible hub. */
    public static boolean debugStart(ServerLevel level,WashedAshoreSavedData data,ResourceLocation hubId,ResourceLocation incidentId,BlockPos commandOrigin){
        HubDefinition hub=HubIncidentRegistry.hubs().get(hubId);HubIncidentDefinition definition=HubIncidentRegistry.incident(incidentId).orElse(null);
        if(hub==null||definition==null||hub.tier()!=definition.tier())return false;
        // A hub id describes a hub type. In multi-instance worlds, target the matching hub nearest
        // the command source instead of silently operating at the original storyline's distant hub.
        WashedAshoreInstance targetAct=data.instances().stream().filter(WashedAshoreInstance::contentReady)
                .filter(act->act.slot(hub.slot())!=null)
                .min(Comparator.comparingDouble(act->act.slot(hub.slot()).distSqr(commandOrigin))).orElse(null);
        if(targetAct==null)return false;
        BlockPos hubCenter=targetAct.slot(hub.slot());HubIncidentState state=data.hubIncident(targetAct.actInstanceId(),hubId);
        if(state.active())finish(level,data,hub,state,level.getGameTime(),false);
        BlockPos center=definition.special().locationSlot().map(targetAct::slot).orElse(hubCenter);if(center==null)return false;
        BlockPos destination=patrolDestination(level,center,definition);List<UUID> spawned=spawnRoster(level,incidentId,definition,center,destination,false);
        if(spawned.isEmpty())return false;long now=level.getGameTime();state.begin(incidentId,now,now+definition.durationTicks(),center,destination,spawned,spawned.getFirst());
        prepareProtected(level,state,incidentId,definition,center);state.opponents().addAll(spawnRoster(level,incidentId,definition,center,destination,true));
        data.dirty();announce(level,hubCenter,hub.playerRadius(),incidentId,"start");
        CampaignCore.LOGGER.info("hub_incident_debug_started hub={} incident={} instance={} center={} members={} protected={} opponents={}",
                hubId,incidentId,targetAct.actInstanceId(),center,spawned.size(),state.protectedEntities().size(),state.opponents().size());
        return true;
    }

    /** Operator/debug entry point: aborts and cleans the active incident at a hub. */
    public static boolean debugStop(ServerLevel level,WashedAshoreSavedData data,ResourceLocation hubId,BlockPos commandOrigin){
        HubDefinition hub=HubIncidentRegistry.hubs().get(hubId);if(hub==null)return false;
        WashedAshoreInstance targetAct=data.instances().stream().filter(WashedAshoreInstance::contentReady)
                .filter(act->act.slot(hub.slot())!=null)
                .min(Comparator.comparingDouble(act->act.slot(hub.slot()).distSqr(commandOrigin))).orElse(null);
        if(targetAct==null)return false;
        HubIncidentState state=data.hubIncident(targetAct.actInstanceId(),hubId);
        if(!state.active())return false;finish(level,data,hub,state,level.getGameTime(),false);return true;
    }

    public static void onEntityDeath(ServerLevel level,Entity entity){
        if(!entity.getTags().contains(MEMBER_TAG))return;WashedAshoreSavedData data=WashedAshoreSavedData.get(level);boolean changed=false;
        for(HubIncidentState state:data.hubIncidentsView().values()){changed|=state.members().remove(entity.getUUID());changed|=state.protectedEntities().remove(entity.getUUID());changed|=state.opponents().remove(entity.getUUID());}
        if(changed)data.dirty();
    }
}
