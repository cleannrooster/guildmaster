package dev.campaigncore.settlers;

import dev.campaigncore.settlers.command.SettlersCommands;
import dev.campaigncore.settlers.config.SettlersConfig;
import dev.campaigncore.settlers.detection.StructureRoleRules;
import dev.campaigncore.settlers.detection.VillageDetector;
import dev.campaigncore.settlers.detection.VillagerSuppressor;
import dev.campaigncore.settlers.entity.data.SettlerDataManager;
import dev.campaigncore.settlers.expansion.FrontierHubRuntimePlacer;
import dev.campaigncore.settlers.registry.SettlersBlocks;
import dev.campaigncore.settlers.registry.SettlersEntities;
import dev.campaigncore.settlers.registry.SettlersItems;
import dev.campaigncore.settlers.settlement.SettlementTicker;
import dev.campaigncore.settlers.situation.SituationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Common entrypoint for the act-owned Settlers subsystem. Passive vanilla-village conversion is
/// retained only as a disabled-by-default legacy adapter.
///
/// Each loader calls {@link #init()} from its own entrypoint; loader-specific wiring (entity
/// attributes, client renderers, config screens) stays in the fabric/neoforge source sets.
///
/// Architecture note: legacy vanilla-village conversion is an optional *adapter* around a generic
/// settlement framework. Settlement profiles, roles, schedules, anchors, and threat states are
/// all independent of vanilla structure identity so hand-authored structures can plug in later.
public final class SettlersMod {
    public static final String MOD_ID = "settlers";
    public static final Logger LOGGER = LoggerFactory.getLogger("Settlers");

    private SettlersMod() {
    }

    public static void init() {
        // Config first: everything after this (conversion, population, threat logic) reads it live.
        SettlersConfig.register();
        SettlersEntities.ENTITY_TYPES.register();
        SettlersBlocks.BLOCKS.register();
        SettlersItems.ITEMS.register();
        SettlersItems.appendToCreativeTabs();
        SettlerDataManager.register();
        StructureRoleRules.register();
        SituationManager.register();
        VillageDetector.register();
        VillagerSuppressor.register();
        FrontierHubRuntimePlacer.register();
        SettlementTicker.register();
        SettlersCommands.register();
        LOGGER.info("Settlers common init complete.");
    }
}
