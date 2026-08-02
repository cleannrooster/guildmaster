package dev.campaigncore.settlers.entity.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/// One entry in the pool of base human skins (`data/<ns>/base_skins/*.json`) a Settler can be
/// randomly assigned. Deliberately separate from {@link SettlerProfile}: the base skin is a random,
/// per-entity, persistent choice unrelated to role or profession — job identity is instead
/// communicated by the profile's {@link SettlerProfile#jobOverlay()}, layered on top at render time.
public record BaseSkin(ResourceLocation texture) {
    public static final Codec<BaseSkin> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("texture").forGetter(BaseSkin::texture)
    ).apply(instance, BaseSkin::new));
}
