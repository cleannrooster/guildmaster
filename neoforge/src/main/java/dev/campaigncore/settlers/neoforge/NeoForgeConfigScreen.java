package dev.campaigncore.settlers.neoforge;

import dev.campaigncore.settlers.config.SettlersConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/// Wires the mod-list "Config" button to the AutoConfig (Cloth Config) screen. Kept in its own class,
/// only referenced behind a client dist check, so a dedicated server never loads the client-only
/// {@link IConfigScreenFactory}.
public final class NeoForgeConfigScreen {
    private NeoForgeConfigScreen() {
    }

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> AutoConfig.getConfigScreen(SettlersConfig.class, parent).get());
    }
}
