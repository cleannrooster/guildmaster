package dev.campaigncore.settlers.situation;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.data.CodecReloadListener;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// Loads `data/<ns>/situations/*.json` via the standard codec reload listener. A settlement's
/// `current_situation` profile field is just the situation id's path; lookups fail soft (missing
/// situation -> nothing staged, logged) so a bad datapack never blocks conversion.
public final class SituationManager {
    private static final Map<ResourceLocation, SituationDefinition> SITUATIONS = new ConcurrentHashMap<>();

    private SituationManager() {
    }

    public static void register() {
        ReloadListenerRegistry.register(PackType.SERVER_DATA,
                new CodecReloadListener<>("situations", SituationDefinition.CODEC, parsed -> {
                    SITUATIONS.clear();
                    SITUATIONS.putAll(parsed);
                }),
                ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "situations"));
    }

    /// Situations are referenced by bare name in a settlement profile (e.g. "damaged_caravan_arrival"),
    /// resolved in the mod's own namespace; packs may still register other ids for direct lookup.
    public static Optional<SituationDefinition> byName(String name) {
        return Optional.ofNullable(SITUATIONS.get(ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, name)));
    }
}
