package dev.campaigncore.washedashore.encounter;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.compat.LegacyCampaignCompatibility;
import dev.campaigncore.config.CampaignServerConfig;
import dev.campaigncore.washedashore.act.*;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.*;
import dev.campaigncore.washedashore.message.CampaignMessages;
import dev.campaigncore.washedashore.message.SettlementDialogueNames;

public final class EncounterManager {
    public static final ResourceLocation UNDERTAKER=id("undertaker"),CONSUMING_DREAD=id("consuming_dread"),
            THRASHER=id("thrasher"),REGIONAL_C=id("regional_boss_c"),RAVEN=id("sculken_raven"),SCULK_SURFACE=id("sculk_surface");
    private static final Set<ResourceLocation> REGIONAL=Set.of(CONSUMING_DREAD,THRASHER,REGIONAL_C);
    private static final ResourceLocation UNDERTAKER_RELOCATED=id("undertaker_relocated_to_settlement");
    private static final ResourceLocation SETTLEMENT_UNDERTAKER_KILLED=id("settlement_undertaker_killed");
    private static final String SETTLEMENT_UNDERTAKER_TAG="campaign_core_washed_ashore_settlement_undertaker";
    private static final String SCULK_AND_SCAVENGE="sculk_and_scavenge";
    private static final Map<ResourceLocation,ResourceLocation> SCULK_AND_SCAVENGE_PREFERRED=Map.of(
            UNDERTAKER,ResourceLocation.fromNamespaceAndPath(SCULK_AND_SCAVENGE,"undertaker"),
            CONSUMING_DREAD,ResourceLocation.fromNamespaceAndPath(SCULK_AND_SCAVENGE,"consuming_dread"),
            RAVEN,ResourceLocation.fromNamespaceAndPath(SCULK_AND_SCAVENGE,"sculken_raven"));
    /** Marks a candidate spawn whose native drops must be suppressed (read by EncounterDropSuppressionMixin). */
    public static final String SUPPRESS_DROPS_TAG="campaign_core_washed_ashore_suppress_drops";
    private static final EncounterDefinitionRegistry DEFINITIONS=new EncounterDefinitionRegistry();
    private static final EncounterCandidatePool CANDIDATES=new EncounterCandidatePool();
    private static final EncounterCandidateSelector SELECTOR=new EncounterCandidateSelector(CANDIDATES);
    /** The slots (encounter ids) a candidate may target; matches the six shipped encounter definitions. */
    private static final Set<ResourceLocation> SLOTS=Set.of(UNDERTAKER,CONSUMING_DREAD,THRASHER,REGIONAL_C,RAVEN,SCULK_SURFACE);
    private EncounterManager(){}
    private static ResourceLocation id(String path){return CampaignCore.washedAshoreId(path);}

    /** Datapack-loaded encounter spawn table (data/campaign_core/campaign_encounters). */
    public static EncounterDefinitionRegistry definitions(){return DEFINITIONS;}
    /** Datapack-loaded encounter-candidate pool (data/campaign_core/campaign_encounter_candidates). */
    public static EncounterCandidatePool candidates(){return CANDIDATES;}
    /** Shared selector over the candidate pool (filter + weighted pick + override application). */
    public static EncounterCandidateSelector selector(){return SELECTOR;}
    /** Valid candidate slot ids (the shipped encounter ids). */
    public static Set<ResourceLocation> slots(){return SLOTS;}

    /**
     * Reload hook for the candidate pool: stamps each file's id onto its candidate, drops entries with an
     * unknown slot or an invalid form (logged, never fatal), groups by slot, and swaps the pool atomically.
     */
    public static void loadCandidates(Map<ResourceLocation,EncounterCandidate> parsed){
        Map<ResourceLocation,List<EncounterCandidate>> grouped=new HashMap<>();
        int kept=0;
        for(Map.Entry<ResourceLocation,EncounterCandidate> entry:parsed.entrySet()){
            EncounterCandidate candidate=entry.getValue().withId(entry.getKey());
            if(!SLOTS.contains(candidate.slot())){
                CampaignCore.LOGGER.warn("combat_candidate_bad_slot candidate={} slot={} expected_one_of={}",candidate.id(),candidate.slot(),SLOTS);
                continue;
            }
            if(!candidate.hasExactlyOneForm()){
                CampaignCore.LOGGER.warn("combat_candidate_bad_form candidate={}; exactly one of entity/entity_tag/raid is required",candidate.id());
                continue;
            }
            grouped.computeIfAbsent(candidate.slot(),k->new ArrayList<>()).add(candidate);
            kept++;
        }
        CANDIDATES.replace(grouped);
        CampaignCore.LOGGER.info("combat_candidates_reloaded count={} slots={}",kept,grouped.keySet());
    }

    /** Materializes the loaded encounter table onto an act, resolving each anchor to a world position. */
    public static void registerDefaults(WashedAshoreInstance act) {
        java.util.Collection<EncounterDefinition> defs=DEFINITIONS.all();
        if(defs.isEmpty()){
            CampaignCore.LOGGER.error("encounter_definitions_missing act={}; no encounters registered",act.actInstanceId());
            return;
        }
        for(EncounterDefinition def:defs){
            BlockPos anchor=resolveAnchor(act,def);
            if(anchor==null){
                CampaignCore.LOGGER.warn("encounter_anchor_unresolved id={} anchor={}",def.id(),def.anchor());
                continue;
            }
            act.encounters().computeIfAbsent(def.id(),key->new EncounterAnchor(
                    key,def.bossEntity(),anchor,anchor.above(),
                    def.activationRadius(),def.resetRadius(),def.oneShot(),def.requiredStage(),def.retryDelayTicks()));
            if(def.placeholder())CampaignCore.LOGGER.info("encounter_registered_placeholder id={} boss={}",def.id(),def.bossEntity());
        }
    }
    private static BlockPos resolveAnchor(WashedAshoreInstance act,EncounterDefinition def){
        BlockPos base=act.slot(def.anchor());
        if(base==null)return null;
        net.minecraft.core.Vec3i o=def.anchorOffset();
        return o.equals(net.minecraft.core.Vec3i.ZERO)?base:base.offset(o.getX(),o.getY(),o.getZ());
    }

    /** Named boss-death consequences referenced by encounter {@code on_complete.handlers}. */
    @FunctionalInterface private interface CompletionEffect {
        void run(ServerLevel level,WashedAshoreSavedData data,EncounterAnchor encounter,ServerPlayer player,WashedAshoreProgress progress);
    }
    private static final Map<String,CompletionEffect> COMPLETION_EFFECTS=Map.of(
            "complete_dread_quest",(level,data,encounter,player,progress)->progress.setDreadQuest(RegionalQuestStage.COMPLETE),
            "complete_crossing_quest",(level,data,encounter,player,progress)->progress.setCrossingQuest(RegionalQuestStage.COMPLETE),
            "regional_objectives_gate",EncounterManager::regionalObjectivesGate,
            "unlock_act_two",(level,data,encounter,player,progress)->WashedAshoreManager.unlockActTwo(level,player));
    public static Set<String> completionHandlers(){return COMPLETION_EFFECTS.keySet();}

    private static void regionalObjectivesGate(ServerLevel level,WashedAshoreSavedData data,EncounterAnchor encounter,
                                               ServerPlayer player,WashedAshoreProgress progress){
        if(hasCompletedRequiredRegionalObjectives(progress)){
            progress.advanceTo(WashedAshoreStage.RAVEN_ROUTE_REVEALED);
            WashedAshoreManager.triggerRavenSettlementEvent(level,data.act());
        }
    }
    public static void tick(ServerLevel level,WashedAshoreSavedData data) {
        if(level!=level.getServer().overworld()||level.getGameTime()%10!=0)return;
        if(data.act().completedWorldObjectives().contains(UNDERTAKER_RELOCATED)
                &&level.players().stream().anyMatch(player->player.blockPosition().distSqr(data.act().settlement())<=128*128))
            ensureSettlementUndertaker(level,data.act());
        for(var instance:data.instances())for(EncounterAnchor encounter:instance.encounters().values()){
            if(encounter.status()==EncounterStatus.COMPLETED)continue;
            if(encounter.awaitingRetry()){
                if(level.getGameTime()>=encounter.retryAt())retryEncounter(level,data,encounter);
                continue;
            }
            if(encounter.status()==EncounterStatus.ACTIVE){validateActive(level,data,instance,encounter);continue;}
            // Dread/Thrasher/Sculk Surface are event-driven; raids are handled by SettlementRaidManager.
            // None of them use the single-boss proximity spawn below.
            if(encounter.encounterId().equals(CONSUMING_DREAD)||encounter.encounterId().equals(THRASHER)
                    ||encounter.encounterId().equals(SCULK_SURFACE)
                    ||DEFINITIONS.get(encounter.encounterId()).map(EncounterDefinition::isRaid).orElse(false))continue;
            for(ServerPlayer player:level.players()){
                WashedAshoreProgress progress=data.player(player.getUUID());
                if(!progress.stage().atLeast(encounter.requiredStage()))continue;
                if(player.blockPosition().distSqr(encounter.anchorPos())<=square(encounter.activationRadius())){
                    activate(level,data,encounter,player);break;
                }
            }
        }
    }
    public static boolean activate(ServerLevel level,WashedAshoreSavedData data,EncounterAnchor encounter,ServerPlayer triggeringPlayer){
        return activate(level,data,encounter,triggeringPlayer,true);
    }
    public static boolean activate(ServerLevel level,WashedAshoreSavedData data,EncounterAnchor encounter,
                                   ServerPlayer triggeringPlayer,boolean tutorialScaled){
        if(encounter.status()==EncounterStatus.COMPLETED)return false;
        if(encounter.activeBossUuid()!=null&&level.getEntity(encounter.activeBossUuid())!=null)return false;
        // Pick (once, then persist) a pool candidate for this slot; null = use the native definition entity.
        EncounterCandidate candidate=resolveSelection(level,data,encounter);
        ResourceLocation entityId=encounter.bossEntityType();
        EntityType<?> type=null;
        if(candidate!=null&&candidate.isSingle()){
            var resolved=SELECTOR.resolveEntityType(candidate,level.random);
            if(resolved.isPresent()){type=resolved.get();entityId=BuiltInRegistries.ENTITY_TYPE.getKey(type);}
            else CampaignCore.LOGGER.warn("combat_candidate_unresolved_at_spawn encounter={} candidate={}",encounter.encounterId(),candidate.id());
        }
        if(type==null){
            type=BuiltInRegistries.ENTITY_TYPE.get(entityId);
            if(type==null||type==EntityType.PIG&& !entityId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG))){
                encounter.fail(level.getGameTime());data.dirty();CampaignCore.LOGGER.error("encounter_spawn_missing_type encounter={} type={}",encounter.encounterId(),entityId);return false;
            }
        }
        Entity boss=type.create(level);
        if(boss==null){encounter.fail(level.getGameTime());data.dirty();return false;}
        CampaignSpawnProtection.protectFromSun(boss);
        Vec3 spawn=resolveClearSpawn(level,boss,encounter.bossSpawnPos());
        boss.moveTo(spawn.x,spawn.y,spawn.z,level.random.nextFloat()*360,0);
        BlockPos p=BlockPos.containing(spawn);
        if(boss instanceof Mob mob)mob.finalizeSpawn(level,level.getCurrentDifficultyAt(p),MobSpawnType.EVENT,null);
        boss.addTag("campaign_core_washed_ashore_encounter="+encounter.encounterId());
        configureCandidate(level,boss,candidate,encounter,tutorialScaled);
        if(!level.addFreshEntity(boss)){encounter.fail(level.getGameTime());data.dirty();return false;}
        encounter.activate(boss.getUUID());data.dirty();
        CampaignCore.LOGGER.info("encounter_activated id={} boss={} uuid={} pos={} tutorial_scaled={}",
                encounter.encounterId(),encounter.bossEntityType(),boss.getUUID(),p,tutorialScaled);
        if(triggeringPlayer!=null){
            CampaignMessages.send(triggeringPlayer,encounter.encounterId().equals(UNDERTAKER)?
                    "undertaker_discovered":"encounter_active");
            if(encounter.encounterId().equals(UNDERTAKER))data.player(triggeringPlayer.getUUID()).advanceTo(WashedAshoreStage.UNDERTAKER_DISCOVERED);
        }
        return true;
    }
    private static void validateActive(ServerLevel level,WashedAshoreSavedData data,dev.campaigncore.washedashore.act.WashedAshoreInstance instance,EncounterAnchor encounter){
        Entity boss=encounter.activeBossUuid()==null?null:level.getEntity(encounter.activeBossUuid());
        if(boss!=null){migrateLegacyEncounterMetadata(boss);encounter.found();updateBossBar(level,encounter);if(!boss.isAlive())complete(level,data,instance,encounter);return;}
        if(!level.hasChunkAt(encounter.bossSpawnPos()))return;
        Entity replacement=findReplacement(level,encounter);
        if(replacement!=null){
            UUID previous=encounter.activeBossUuid();
            replacement.addTag(encounterTag(encounter.encounterId()));
            encounter.activate(replacement.getUUID());data.dirty();updateBossBar(level,encounter);
            CampaignCore.LOGGER.info("encounter_boss_rebound id={} previous_uuid={} replacement_uuid={} type={}",
                    encounter.encounterId(),previous,replacement.getUUID(),BuiltInRegistries.ENTITY_TYPE.getKey(replacement.getType()));
            return;
        }
        if(encounter.noteMissing()>=6){
            // The boss vanished without a death (unloaded/removed): treat as abandoned and
            // schedule an automatic retry rather than resetting the quest immediately.
            CampaignCore.LOGGER.warn("encounter_boss_missing_abandoned id={} uuid={}",encounter.encounterId(),encounter.activeBossUuid());
            encounter.abandon(level.getGameTime());data.dirty();
        }
    }
    public static boolean onEntityDeath(ServerLevel level,Entity entity){
        WashedAshoreSavedData data=WashedAshoreSavedData.get(level);
        if(entity.getTags().contains(SETTLEMENT_UNDERTAKER_TAG)){
            data.act().completedWorldObjectives().add(SETTLEMENT_UNDERTAKER_KILLED);
            data.dirty();
            CampaignCore.LOGGER.info("settlement_undertaker_killed uuid={} pos={}",entity.getUUID(),entity.blockPosition());
            return true;
        }
        for(var instance:data.instances())for(EncounterAnchor e:instance.encounters().values())
            if(entity.getUUID().equals(e.activeBossUuid())||matchesEncounterIdentity(entity,e)){
                dropCampaignLoot(level,entity,e);return complete(level,data,instance,e);
            }
        return false;
    }

    private static Entity findReplacement(ServerLevel level,EncounterAnchor encounter){
        AABB area=new AABB(encounter.anchorPos()).inflate(encounter.resetRadius());
        return level.getEntities((Entity)null,area,entity->entity.isAlive()&&matchesEncounterIdentity(entity,encounter))
                .stream().min(Comparator.comparingDouble(entity->entity.blockPosition().distSqr(encounter.anchorPos()))).orElse(null);
    }

    private static boolean matchesEncounterIdentity(Entity entity,EncounterAnchor encounter){
        if(encounter.status()!=EncounterStatus.ACTIVE)return false;
        if(entity.blockPosition().distSqr(encounter.anchorPos())>square(encounter.resetRadius()))return false;
        if(entity.getTags().contains(encounterTag(encounter.encounterId())))return true;
        EncounterCandidate candidate=encounter.selectedCandidate()==null?null:CANDIDATES.byId(encounter.selectedCandidate()).orElse(null);
        return candidate!=null&&entity.hasCustomName()
                &&entity.getCustomName().getString().equals(encounterDisplayName(candidate,encounter.encounterId()).getString());
    }

    private static String encounterTag(ResourceLocation id){return "campaign_core_washed_ashore_encounter="+id;}
    public static boolean complete(ServerLevel level,WashedAshoreSavedData data,ResourceLocation id){
        EncounterAnchor encounter=data.act().encounters().get(id);if(encounter==null||encounter.status()==EncounterStatus.COMPLETED)return false;
        return complete(level,data,data.act(),encounter);
    }
    public static boolean complete(ServerLevel level,WashedAshoreSavedData data,EncounterAnchor encounter){
        for(var instance:data.instances())if(instance.encounters().containsValue(encounter))return complete(level,data,instance,encounter);
        return false;
    }
    private static boolean complete(ServerLevel level,WashedAshoreSavedData data,dev.campaigncore.washedashore.act.WashedAshoreInstance instance,EncounterAnchor encounter){
        ResourceLocation id=encounter.encounterId();if(encounter.status()==EncounterStatus.COMPLETED)return false;
        encounter.complete();EncounterBossBars.close(id);instance.completedWorldObjectives().add(id);data.act().completedWorldObjectives().add(id);
        EncounterCompletion completion=DEFINITIONS.get(id).map(EncounterDefinition::onComplete).orElse(EncounterCompletion.DEFAULT);
        Component name=Component.translatable("encounter.campaign_core.washed_ashore."+id.getPath());
        for(ServerPlayer player:level.players()){
            if(player.blockPosition().distSqr(encounter.anchorPos())>square(encounter.resetRadius()))continue;
            WashedAshoreProgress progress=data.player(player.getUUID());progress.defeat(id);
            for(WashedAshoreStage stage:completion.advanceTo())progress.advanceTo(stage);
            for(String handler:completion.handlers()){
                CompletionEffect effect=COMPLETION_EFFECTS.get(handler);
                if(effect!=null)effect.run(level,data,encounter,player,progress);
            }
            DEFINITIONS.get(id).map(EncounterDefinition::rewardLootTable).ifPresent(table->
                    grantRewardLoot(level,player,table,id));
            CampaignMessages.send(player,completion.message(),name);
        }
        data.dirty();CampaignCore.LOGGER.info("encounter_completed id={}",id);return true;
    }
    private static void grantRewardLoot(ServerLevel level,ServerPlayer player,ResourceLocation tableId,ResourceLocation encounterId){
        var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,tableId);
        var table=level.getServer().reloadableRegistries().getLootTable(key);
        var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,player.position())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        for(var stack:table.getRandomItems(params))if(!stack.isEmpty()&&!player.getInventory().add(stack))player.drop(stack,false);
        CampaignCore.LOGGER.info("encounter_reward_granted encounter={} player={} table={}",encounterId,player.getUUID(),tableId);
    }
    public static void completeUndertakerBySettlementArrival(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player){
        EncounterAnchor encounter=data.act().encounters().get(UNDERTAKER);
        if(encounter==null)return;
        if(encounter.status()!=EncounterStatus.COMPLETED){
            if(encounter.activeBossUuid()!=null){
                Entity active=level.getEntity(encounter.activeBossUuid());
                if(active!=null)active.discard();
            }
            encounter.complete();
            data.act().completedWorldObjectives().add(UNDERTAKER);
            data.act().completedWorldObjectives().add(UNDERTAKER_RELOCATED);
            CampaignCore.LOGGER.info("undertaker_encounter_bypassed_at_settlement player={} settlement={}",
                    player.getUUID(),data.act().settlement());
        }
        WashedAshoreProgress progress=data.player(player.getUUID());
        progress.defeat(UNDERTAKER);
        progress.advanceTo(WashedAshoreStage.UNDERTAKER_DEFEATED);
        ensureSettlementUndertaker(level,data.act());
        data.dirty();
        CampaignMessages.send(player,"undertaker_relocated",SettlementDialogueNames.primary(level,data.act()));
    }
    private static void ensureSettlementUndertaker(ServerLevel level,WashedAshoreInstance act){
        if(act.completedWorldObjectives().contains(SETTLEMENT_UNDERTAKER_KILLED))return;
        BlockPos center=act.settlement();
        AABB search=new AABB(center).inflate(64);
        var existing=level.getEntities((Entity)null,search,entity->entity.getTags().contains(SETTLEMENT_UNDERTAKER_TAG));
        if(!existing.isEmpty()){
            if(existing.getFirst() instanceof Mob mob&&mob.getTarget()==null){
                var nearest=level.getNearestPlayer(mob,64);
                if(nearest!=null)mob.setTarget(nearest);
            }
            return;
        }
        EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("sculk_and_scavenge:undertaker"));
        if(type==null)return;
        Entity entity=type.create(level);
        if(entity==null)return;
        CampaignSpawnProtection.protectFromSun(entity);
        Vec3 spawn=resolveClearSpawn(level,entity,center.above());
        entity.moveTo(spawn.x,spawn.y,spawn.z,0,0);
        entity.addTag(SETTLEMENT_UNDERTAKER_TAG);
        scaleAsTutorialBoss(entity);
        if(entity instanceof Mob mob){
            mob.setPersistenceRequired();
            var nearest=level.getNearestPlayer(mob,64);
            if(nearest!=null)mob.setTarget(nearest);
        }
        if(level.addFreshEntity(entity))
            CampaignCore.LOGGER.info("settlement_undertaker_spawned uuid={} pos={}",entity.getUUID(),spawn);
    }
    public static boolean hasCompletedRequiredRegionalObjectives(WashedAshoreProgress progress){
        return REGIONAL.stream().filter(progress.defeatedBosses()::contains).count()>=2;
    }
    /** Lazily selects (and persists) a candidate for the encounter's slot; returns null to use the native entity. */
    private static EncounterCandidate resolveSelection(ServerLevel level,WashedAshoreSavedData data,EncounterAnchor encounter){
        ResourceLocation preferred=SCULK_AND_SCAVENGE_PREFERRED.get(encounter.encounterId());
        if(CampaignServerConfig.preferSculkAndScavengeEncounters()&&preferred!=null
                &&SELECTOR.modPresent(SCULK_AND_SCAVENGE)
                &&EncounterCandidateSelector.entityResolves(preferred)){
            Optional<EncounterCandidate> nativeCandidate=
                    SELECTOR.selectEntity(level,encounter.encounterId(),encounter.bossSpawnPos(),preferred);
            ResourceLocation selection=nativeCandidate.map(EncounterCandidate::id).orElse(null);
            if(!Objects.equals(encounter.selectedCandidate(),selection)){
                encounter.setSelectedCandidate(selection);data.dirty();
            }
            CampaignCore.LOGGER.info("combat_candidate_sculk_and_scavenge_preferred encounter={} entity={} candidate={}",
                    encounter.encounterId(),preferred,selection);
            // Raven has no candidate entry because its native encounter definition already names the
            // Sculk and Scavenge entity. A null candidate deliberately takes that native path.
            return nativeCandidate.orElse(null);
        }
        if(encounter.selectedCandidate()!=null)return CANDIDATES.byId(encounter.selectedCandidate()).orElse(null);
        Optional<EncounterCandidate> selected=SELECTOR.select(level,encounter.encounterId(),encounter.bossSpawnPos());
        if(selected.isPresent()){
            encounter.setSelectedCandidate(selected.get().id());data.dirty();
            CampaignCore.LOGGER.info("combat_candidate_selected encounter={} candidate={}",encounter.encounterId(),selected.get().id());
        }
        return selected.orElse(null);
    }
    /** Applies candidate overrides/presentation, or the legacy tutorial halving when no candidate sets health. */
    private static void configureCandidate(ServerLevel level,Entity boss,EncounterCandidate candidate,EncounterAnchor encounter,boolean tutorialScaled){
        if(candidate!=null){
            SELECTOR.applyOverrides(boss,candidate);
            if(candidate.suppressNativeDrops())boss.addTag(SUPPRESS_DROPS_TAG);
            if(candidate.bossBar())EncounterBossBars.open(encounter.encounterId(),encounterDisplayName(candidate,encounter.encounterId()));
        }
        boolean candidateSetsHealth=candidate!=null&&candidate.attributes().maxHealth().isPresent();
        if(tutorialScaled&&!candidateSetsHealth)scaleAsTutorialBoss(boss);
    }
    private static Component encounterDisplayName(EncounterCandidate candidate,ResourceLocation encounterId){
        if(candidate!=null&&candidate.displayName().isPresent())return EncounterCandidateSelector.displayComponent(candidate.displayName().get());
        return Component.translatable("encounter.campaign_core.washed_ashore."+encounterId.getPath());
    }
    /** Rolls a candidate's campaign loot table (if any) and drops the items at the fallen boss's position. */
    private static void dropCampaignLoot(ServerLevel level,Entity boss,EncounterAnchor encounter){
        EncounterCandidate candidate=encounter.selectedCandidate()==null?null:CANDIDATES.byId(encounter.selectedCandidate()).orElse(null);
        if(candidate==null||candidate.campaignLootTable().isEmpty())return;
        var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,candidate.campaignLootTable().get());
        net.minecraft.world.level.storage.loot.LootTable table=level.getServer().reloadableRegistries().getLootTable(key);
        // CHEST param set: ORIGIN only (THIS_ENTITY is not an allowed CHEST param and would fail validation).
        net.minecraft.world.level.storage.loot.LootParams params=new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,boss.position())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        Vec3 at=boss.position();
        for(net.minecraft.world.item.ItemStack stack:table.getRandomItems(params)){
            if(stack.isEmpty())continue;
            net.minecraft.world.entity.item.ItemEntity item=new net.minecraft.world.entity.item.ItemEntity(level,at.x,at.y+0.5,at.z,stack);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        CampaignCore.LOGGER.info("combat_campaign_loot_dropped encounter={} candidate={} table={}",encounter.encounterId(),candidate.id(),candidate.campaignLootTable().get());
    }
    private static void scaleAsTutorialBoss(Entity entity){
        if(!(entity instanceof LivingEntity living))return;
        entity.addTag("campaign_core_washed_ashore_tutorial_boss");
        var health=living.getAttribute(Attributes.MAX_HEALTH);
        if(health!=null)health.setBaseValue(Math.max(1,health.getBaseValue()*.5));
        living.setHealth(living.getMaxHealth());
    }
    private static void migrateLegacyEncounterMetadata(Entity entity){
        for(String tag:List.copyOf(entity.getTags()))if(tag.startsWith(LegacyCampaignCompatibility.ENCOUNTER_TAG_PREFIX)){
            entity.removeTag(tag);
            entity.addTag("campaign_core_washed_ashore_encounter="+tag.substring(LegacyCampaignCompatibility.ENCOUNTER_TAG_PREFIX.length()));
        }
        if(entity.removeTag(LegacyCampaignCompatibility.TUTORIAL_BOSS_TAG))
            entity.addTag("campaign_core_washed_ashore_tutorial_boss");
    }
    public static void reset(WashedAshoreSavedData data,ResourceLocation id){EncounterAnchor e=data.act().encounters().get(id);if(e!=null){e.reset();data.dirty();CampaignCore.LOGGER.info("encounter_reset id={}",id);}}

    /** Debug: force a slot's encounter to begin now, routing to the manager that owns that slot. */
    public static boolean debugStart(ServerLevel level,WashedAshoreSavedData data,ResourceLocation slot,ServerPlayer player){
        EncounterAnchor e=data.act().encounters().get(slot);
        if(e==null)return false;
        if(slot.equals(REGIONAL_C)){SettlementRaidManager.onOtherSettlementArrival(level,data,player);return true;}
        if(slot.equals(SCULK_SURFACE))return SculkSurfaceManager.debugStart(level,data,player);
        if(slot.equals(THRASHER)){CrossingHordeManager.begin(level,data,e,player);return true;}
        return activate(level,data,e,player,slot.equals(UNDERTAKER));
    }

    /** Debug: unload a slot's active encounter, clear its selection and world flags, and return it to DORMANT. */
    public static boolean abortAndClear(ServerLevel level,WashedAshoreSavedData data,ResourceLocation slot){
        EncounterAnchor e=data.act().encounters().get(slot);
        if(e==null)return false;
        unloadBoss(level,e);
        if(slot.equals(REGIONAL_C))SettlementRaidManager.reset(level,data);
        if(slot.equals(THRASHER))CrossingHordeManager.reset(data);
        if(slot.equals(SCULK_SURFACE))SculkSurfaceManager.reset(level,data);
        data.act().completedWorldObjectives().remove(slot);
        e.reset();
        data.dirty();
        CampaignCore.LOGGER.info("combat_encounter_aborted slot={}",slot);
        return true;
    }
    /** Fails an encounter: unloads its boss and starts the retry cooldown. */
    public static boolean fail(ServerLevel level,WashedAshoreSavedData data,ResourceLocation id){return endEncounter(level,data,id,false);}
    /** Abandons an encounter: unloads its boss and starts the retry cooldown. */
    public static boolean abandon(ServerLevel level,WashedAshoreSavedData data,ResourceLocation id){return endEncounter(level,data,id,true);}
    private static boolean endEncounter(ServerLevel level,WashedAshoreSavedData data,ResourceLocation id,boolean abandoned){
        EncounterAnchor encounter=data.act().encounters().get(id);
        if(encounter==null||encounter.status()==EncounterStatus.COMPLETED)return false;
        unloadBoss(level,encounter);
        long now=level.getGameTime();
        if(abandoned)encounter.abandon(now);else encounter.fail(now);
        data.dirty();
        CampaignCore.LOGGER.info("encounter_{} id={} delay_ticks={} retry_at={}",
                abandoned?"abandoned":"failed",id,encounter.retryDelayTicks(),encounter.retryAt());
        return true;
    }
    /** Discards the encounter's live boss entity if it is loaded. */
    private static void unloadBoss(ServerLevel level,EncounterAnchor encounter){
        EncounterBossBars.close(encounter.encounterId());
        if(encounter.activeBossUuid()==null)return;
        Entity boss=level.getEntity(encounter.activeBossUuid());
        if(boss!=null){boss.discard();CampaignCore.LOGGER.info("encounter_boss_unloaded id={} uuid={}",encounter.encounterId(),encounter.activeBossUuid());}
    }
    /** Rebuilds (post-reload) and refreshes an active encounter's boss bar when its candidate wants one. */
    private static void updateBossBar(ServerLevel level,EncounterAnchor encounter){
        EncounterCandidate candidate=encounter.selectedCandidate()==null?null:CANDIDATES.byId(encounter.selectedCandidate()).orElse(null);
        if(candidate==null||!candidate.bossBar())return;
        EncounterBossBars.open(encounter.encounterId(),encounterDisplayName(candidate,encounter.encounterId()));
        EncounterBossBars.updateFrom(level,encounter.encounterId(),encounter.activeBossUuid());
    }
    /** Cooldown elapsed: return the encounter to its available (DORMANT) state and re-offer its quest. */
    private static void retryEncounter(ServerLevel level,WashedAshoreSavedData data,EncounterAnchor encounter){
        unloadBoss(level,encounter);
        // Restore the encounter to its authored anchor (a quest may have relocated it) before reopening.
        EncounterDefinition def=DEFINITIONS.get(encounter.encounterId()).orElse(null);
        if(def!=null){BlockPos anchor=resolveAnchor(data.act(),def);if(anchor!=null)encounter.relocate(anchor,anchor.above());}
        encounter.reset();
        RegionalQuestManager.reopenEncounterQuest(level,data,encounter.encounterId());
        data.dirty();
        CampaignCore.LOGGER.info("encounter_retry_available id={} anchor={}",encounter.encounterId(),encounter.anchorPos());
    }
    /**
     * Resolves a spawn near {@code preferred} where the boss's whole body fits with room to move, so
     * large bosses don't materialise inside terrain or walls. The requested spot is already placed close
     * to the player; this spirals outward over surface columns and returns the first that clears the
     * boss's full bounding box on solid ground, falling back to the requested column if none do.
     */
    public static Vec3 resolveClearSpawn(ServerLevel level,Entity boss,BlockPos preferred){
        for(int i=0;i<64;i++){
            double angle=i*2.399963229728653,radius=i==0?0:Math.min(24,1+i*.6);
            int x=preferred.getX()+Mth.floor(Math.cos(angle)*radius);
            int z=preferred.getZ()+Mth.floor(Math.sin(angle)*radius);
            int feetY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            if(fitsForBoss(level,boss,x,feetY,z))return new Vec3(x+.5,feetY,z+.5);
        }
        int feetY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,preferred.getX(),preferred.getZ());
        return new Vec3(preferred.getX()+.5,feetY,preferred.getZ()+.5);
    }
    /** A boss fits where it stands on a sturdy floor and its full body is clear of block collisions. */
    private static boolean fitsForBoss(ServerLevel level,Entity boss,int x,int feetY,int z){
        BlockPos ground=new BlockPos(x,feetY-1,z);
        if(!level.getBlockState(ground).isFaceSturdy(level,ground,Direction.UP))return false;
        AABB body=boss.getType().getDimensions().makeBoundingBox(x+.5,feetY,z+.5);
        return level.noCollision(boss,body);
    }
    private static double square(double value){return value*value;}
}
