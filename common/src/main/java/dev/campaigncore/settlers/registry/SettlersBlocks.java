package dev.campaigncore.settlers.registry;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.settlers.SettlersMod;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/// Static decorative blocks used as workstation props for professions with no vanilla equivalent —
/// see {@link dev.campaigncore.settlers.detection.WagonRepairPlacer}. Placeholder shape/texture, same
/// "functional now, hand-polish later" tier as the Settler rig itself.
public final class SettlersBlocks {
    // Created against campaign_core's mod id so NeoForge can resolve the mod event bus (see
    // SettlersEntities); ids stay in the settlers: namespace via explicit ResourceLocations.
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(CampaignCore.MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<Block> WAGON_REPAIR_BENCH =
            BLOCKS.register(ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "wagon_repair_bench"),
                    () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f)
                    .sound(SoundType.WOOD)));

    private SettlersBlocks() {
    }
}
