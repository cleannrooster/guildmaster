package dev.campaigncore.settlers.entity.data;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.data.CodecReloadListener;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// Loads all Settler datapack definitions (profiles, equipment, base skins) via server data reload
/// listeners, so `/reload` hot-swaps them. Lookups fail soft (Optional/logged) — a missing profile or
/// empty skin pool must never crash a settlement, it just produces a default-textured generic Settler.
public final class SettlerDataManager {
    private static final Map<ResourceLocation, SettlerProfile> PROFILES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, EquipmentProfile> EQUIPMENT = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, BaseSkin> BASE_SKINS = new ConcurrentHashMap<>();

    private SettlerDataManager() {
    }

    public static void register() {
        ReloadListenerRegistry.register(PackType.SERVER_DATA,
                new CodecReloadListener<>("settler_profiles", SettlerProfile.CODEC, parsed -> {
                    PROFILES.clear();
                    PROFILES.putAll(parsed);
                }),
                ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "settler_profiles"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA,
                new CodecReloadListener<>("equipment_profiles", EquipmentProfile.CODEC, parsed -> {
                    EQUIPMENT.clear();
                    EQUIPMENT.putAll(parsed);
                }),
                ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "equipment_profiles"));
        ReloadListenerRegistry.register(PackType.SERVER_DATA,
                new CodecReloadListener<>("base_skins", BaseSkin.CODEC, parsed -> {
                    BASE_SKINS.clear();
                    BASE_SKINS.putAll(parsed);
                }),
                ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "base_skins"));
    }

    public static Optional<SettlerProfile> profile(ResourceLocation id) {
        return Optional.ofNullable(PROFILES.get(id));
    }

    public static Optional<EquipmentProfile> equipment(ResourceLocation id) {
        return Optional.ofNullable(EQUIPMENT.get(id));
    }

    public static Set<ResourceLocation> profileIds() {
        return PROFILES.keySet();
    }

    /// A random base skin texture from the pool, or empty if no base skins loaded (datapack missing
    /// or still loading) — caller falls back to {@link SettlerProfile#DEFAULT_TEXTURE}.
    public static Optional<ResourceLocation> randomBaseSkin(RandomSource random) {
        List<BaseSkin> skins = new ArrayList<>(BASE_SKINS.values());
        if (skins.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(skins.get(random.nextInt(skins.size())).texture());
    }
}
