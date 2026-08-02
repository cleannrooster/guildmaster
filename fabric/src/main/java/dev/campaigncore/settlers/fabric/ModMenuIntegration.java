package dev.campaigncore.settlers.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.campaigncore.settlers.config.SettlersConfig;
import me.shedaniel.autoconfig.AutoConfig;

/// ModMenu hook: the mod's "Configure" button opens the AutoConfig (Cloth Config) screen. ModMenu is a
/// compile-only dependency — this entrypoint is only instantiated when ModMenu is installed.
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfig.getConfigScreen(SettlersConfig.class, parent).get();
    }
}
