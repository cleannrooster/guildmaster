package dev.campaigncore.settlers;

import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;

/// Base for tests that touch Minecraft codecs/registries. {@link Bootstrap#bootStrap()} is idempotent,
/// so it is safe for every subclass to request it.
public abstract class MinecraftTestBase {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }
}
