package dev.campaigncore;

import dev.campaigncore.api.CampaignApi;
import dev.campaigncore.command.CampaignCommands;
import dev.campaigncore.data.CampaignSavedData;
import dev.campaigncore.data.CampaignDefinitionLoader;
import dev.campaigncore.data.CampaignMessageDefinitionLoader;
import dev.campaigncore.data.EncounterDefinitionLoader;
import dev.campaigncore.data.LocationTriggerLoader;
import dev.campaigncore.data.WashedAshoreConfigLoader;
import dev.campaigncore.washedashore.act.WashedAshoreManager;
import dev.campaigncore.washedashore.act.RegionalQuestManager;
import dev.campaigncore.washedashore.command.WashedAshoreCommands;
import dev.campaigncore.washedashore.encounter.EncounterManager;
import dev.campaigncore.washedashore.incident.*;
import dev.campaigncore.washedashore.worldgen.CampaignWorldgen;
import dev.campaigncore.washedashore.recovery.ProneRecoveryManager;
import dev.campaigncore.washedashore.registry.CampaignItems;
import dev.campaigncore.network.CampaignNetwork;
import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.config.CampaignServerConfig;
import dev.architectury.event.events.common.*;
import dev.architectury.event.EventResult;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CampaignCore {
    public static final String MOD_ID = "campaign_core";
    public static final ResourceLocation WASHED_ASHORE = id("washed_ashore");
    public static final Logger LOGGER = LoggerFactory.getLogger("Campaign Core");
    private static boolean initialized;

    private CampaignCore() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static ResourceLocation washedAshoreId(String path) {
        return id("washed_ashore/" + path);
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        CampaignServerConfig.load();
        // Settlers subsystem first: its own init() loads SettlersConfig before any conversion/
        // population logic, and its DeferredRegisters must run during mod construction like ours.
        SettlersMod.init();
        CampaignWorldgen.register();
        CampaignApi.registry().register(WASHED_ASHORE, 1);
        ReloadListenerRegistry.register(PackType.SERVER_DATA,new CampaignDefinitionLoader(),
                id("campaign_definitions"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA,new CampaignMessageDefinitionLoader(),
                id("campaign_messages"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA,new EncounterDefinitionLoader(),
                id("campaign_encounters"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA,new dev.campaigncore.settlers.data.CodecReloadListener<>(
                        "campaign_encounter_candidates",
                        dev.campaigncore.washedashore.encounter.EncounterCandidate.CODEC,
                        EncounterManager::loadCandidates),
                id("campaign_encounter_candidates"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA,new LocationTriggerLoader(),
                id("campaign_location_triggers"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA,new dev.campaigncore.settlers.data.CodecReloadListener<>(
                        "campaign_hubs",HubDefinition.CODEC,HubIncidentRegistry::loadHubs),id("campaign_hubs"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA,new dev.campaigncore.settlers.data.CodecReloadListener<>(
                        "campaign_hub_incidents",HubIncidentDefinition.CODEC,HubIncidentRegistry::loadIncidents),id("campaign_hub_incidents"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA,new WashedAshoreConfigLoader(),
                id("campaign_config"));
        CampaignNetwork.registerServer();
        CampaignItems.register();
        // Retry once the physical server and its game directory are fully available. This also
        // guarantees the default file is materialized on dedicated servers.
        LifecycleEvent.SERVER_LEVEL_LOAD.register(level -> CampaignServerConfig.load());
        LifecycleEvent.SERVER_LEVEL_LOAD.register(WashedAshoreManager::onLevelLoad);
        LifecycleEvent.SERVER_LEVEL_LOAD.register(CampaignCore::migrateLegacyWorld);
        TickEvent.SERVER_LEVEL_POST.register(WashedAshoreManager::tick);
        TickEvent.SERVER_LEVEL_POST.register(ProneRecoveryManager::tick);
        PlayerEvent.PLAYER_JOIN.register(WashedAshoreManager::onJoin);
        PlayerEvent.PLAYER_JOIN.register(ProneRecoveryManager::onJoin);
        PlayerEvent.PLAYER_CLONE.register((oldPlayer,newPlayer,wonGame)->{
            if(!wonGame)ProneRecoveryManager.beginDeathRecovery(newPlayer);
        });
        PlayerEvent.CHANGE_DIMENSION.register((player,oldLevel,newLevel)->ProneRecoveryManager.onDimensionChange(player));
        PlayerEvent.ATTACK_ENTITY.register((player,level,target,hand,result)->
                player instanceof net.minecraft.server.level.ServerPlayer serverPlayer&&ProneRecoveryManager.blocksAttack(serverPlayer)
                        ?EventResult.interruptFalse():EventResult.pass());
        EntityEvent.LIVING_HURT.register((entity,source,amount)->
                entity instanceof net.minecraft.server.level.ServerPlayer player&&ProneRecoveryManager.protectedFromDamage(player)
                        ?EventResult.interruptFalse():EventResult.pass());
        EntityEvent.LIVING_DEATH.register((entity,source)->{
            if(entity.level() instanceof ServerLevel level){
                RegionalQuestManager.onLivestockDeath(level,entity,source);
                dev.campaigncore.washedashore.act.SculkSurfaceManager.onMobDeath(level,entity,source);
                EncounterManager.onEntityDeath(level,entity);
                HubIncidentManager.onEntityDeath(level,entity);
            }
            return EventResult.pass();
        });
        CampaignCommands.register();
        WashedAshoreCommands.register();
        LOGGER.info("Campaign Core API initialized with bundled campaign {}", WASHED_ASHORE);
    }

    public static void migrateLegacyWorld(ServerLevel level) {
        if (level == level.getServer().overworld()) CampaignSavedData.get(level).migrateLegacy(level);
    }
}
