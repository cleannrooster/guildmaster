package dev.campaigncore.settlers.production;

import dev.campaigncore.settlers.SettlersMod;
import net.minecraft.resources.ResourceLocation;

/// Identifiers for the abstract, settlement-level resources production accumulates. These are *not*
/// Minecraft items — they never enter a chest or inventory; they are bookkeeping quantities on a
/// settlement's {@link ProductionState}.
public final class SettlementResources {
    public static final ResourceLocation FOOD = id("food");
    public static final ResourceLocation ANIMAL_GOODS = id("animal_goods");
    public static final ResourceLocation FISH = id("fish");

    private SettlementResources() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, path);
    }
}
