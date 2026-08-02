package dev.campaigncore.washedashore.encounter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Optional;

/**
 * Optional biome/environment gate on an encounter candidate. All three checks are ANDed and act as a hard
 * filter (fine-grained preference is expressed through candidate weight): the anchor biome must match at
 * least one tag in {@code biomeTagsAny} (when non-empty), must match none in {@code biomeTagsNone}, and
 * must satisfy the coarse {@link EnvironmentType} (when present). An absent restriction matches everywhere.
 */
public record EnvironmentRestriction(
        List<ResourceLocation> biomeTagsAny,
        List<ResourceLocation> biomeTagsNone,
        Optional<EnvironmentType> environment
) {
    public static final EnvironmentRestriction ANY =
            new EnvironmentRestriction(List.of(), List.of(), Optional.empty());

    private static final Codec<EnvironmentType> ENV_CODEC =
            Codec.STRING.xmap(s -> EnvironmentType.valueOf(s.toUpperCase(java.util.Locale.ROOT)),
                    EnvironmentType::getSerializedName);

    public static final Codec<EnvironmentRestriction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("biome_tags_any", List.of()).forGetter(EnvironmentRestriction::biomeTagsAny),
            ResourceLocation.CODEC.listOf().optionalFieldOf("biome_tags_none", List.of()).forGetter(EnvironmentRestriction::biomeTagsNone),
            ENV_CODEC.optionalFieldOf("environment").forGetter(EnvironmentRestriction::environment)
    ).apply(instance, EnvironmentRestriction::new));

    public boolean matches(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        if (!biomeTagsAny.isEmpty()
                && biomeTagsAny.stream().noneMatch(tag -> biome.is(TagKey.create(Registries.BIOME, tag)))) {
            return false;
        }
        for (ResourceLocation tag : biomeTagsNone) {
            if (biome.is(TagKey.<Biome>create(Registries.BIOME, tag))) return false;
        }
        return environment.map(type -> type.matches(level, pos)).orElse(true);
    }
}
