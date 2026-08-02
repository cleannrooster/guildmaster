package dev.campaigncore.settlers.config;

import dev.campaigncore.settlers.SettlersMod;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraft.util.Mth;

import java.util.List;

/// Cloth Config (AutoConfig) settings, stored at `config/settlers.json`; the in-game screen comes
/// from ModMenu (Fabric) or the mod-list Config button (NeoForge).
///
/// Gameplay code reads these values live (per conversion / per spawn), so edits apply to the next
/// converted settlement without a restart. Scoped to what a pack or server owner would actually
/// tune: the legacy conversion adapter and active settlement systems. New settlements are authored
/// by campaign acts unless a server owner explicitly enables the legacy adapter.
@Config(name = SettlersMod.MOD_ID)
public final class SettlersConfig implements ConfigData {

    @ConfigEntry.Category("conversion")
    @ConfigEntry.Gui.TransitiveObject
    public Conversion conversion = new Conversion();

    @ConfigEntry.Category("population")
    @ConfigEntry.Gui.TransitiveObject
    public Population population = new Population();

    public static final class Conversion {
        @ConfigEntry.Gui.Tooltip
        // Legacy compatibility only. Act-driven settlement creation does not consult this switch.
        public boolean enableSettlementConversion = false;
        @ConfigEntry.Gui.Tooltip
        public double conversionChance = 0.25D;

        public List<String> includedStructureKeywords = List.of(
                "village",
                "town",
                "settlement",
                "hamlet"
        );

        public List<String> conditionalStructureKeywords = List.of(
                "outpost",
                "colony",
                "camp",
                "homestead",
                "trading_post",
                "crossroads"
        );

        public List<String> excludedStructureKeywords = List.of(
                "pillager",
                "illager",
                "bandit",
                "zombie",
                "ruined",
                "abandoned",
                "dungeon"
        );

        public List<String> explicitStructureIncludes = List.of();
        public List<String> explicitStructureExcludes = List.of();

        public boolean requireInfrastructureQualification = true;
        public int minimumStructurePieces = 3;
        public int minimumBeds = 2;
        public boolean requireFarmInfrastructure = true;
        public int minimumWorkAnchors = 0;
        /// A candidate must be able to support at least this many permanent residents (estimated from
        /// its beds and food infrastructure) to become a settlement at generation.
        public int minimumSupportedPopulation = 12;

        public boolean removeVillagersFromConvertedSettlements = false;
    }

    public static final class Population {
        @ConfigEntry.Gui.Tooltip
        public boolean generateSettlers = true;
        @ConfigEntry.Gui.Tooltip
        public boolean enableTravelers = true;
        @ConfigEntry.Gui.Tooltip
        public boolean enableThreatResponses = true;
    }

    /// Clamp hand-edited JSON back into sane ranges instead of crashing or fighting the game.
    @Override
    public void validatePostLoad() {
        conversion.conversionChance = Mth.clamp(conversion.conversionChance, 0.0, 1.0);

        conversion.minimumStructurePieces =
                Math.max(1, conversion.minimumStructurePieces);

        conversion.minimumBeds =
                Math.max(0, conversion.minimumBeds);


        conversion.minimumWorkAnchors =
                Math.max(0, conversion.minimumWorkAnchors);

        conversion.minimumSupportedPopulation =
                Math.max(0, conversion.minimumSupportedPopulation);
        if (conversion.includedStructureKeywords == null) {
            conversion.includedStructureKeywords = List.of();
        }
    }

    public static void register() {
        AutoConfig.register(SettlersConfig.class, GsonConfigSerializer::new);
        // AutoConfig loads defaults when no file exists, but it does not guarantee that those
        // defaults are persisted until a config screen or another caller saves the holder.
        // Dedicated servers have no config screen, so create config/settlers.json at startup.
        AutoConfig.getConfigHolder(SettlersConfig.class).save();
    }

    public static SettlersConfig get() {
        return AutoConfig.getConfigHolder(SettlersConfig.class).getConfig();
    }
}
