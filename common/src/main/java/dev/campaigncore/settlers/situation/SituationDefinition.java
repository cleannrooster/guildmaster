package dev.campaigncore.settlers.situation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.List;

/// Datapack definition of a settlement "current situation"
/// (`data/<ns>/situations/*.json`) — the design's requirement that a settlement communicate "a
/// current situation or small event occurring when the player arrives", without needing a full quest
/// chain. A situation is: some temporary travelers, some decorative prop blocks placed near the
/// settlement's arrival point, and an ambient bark broadcast to nearby players.
public record SituationDefinition(
        List<TravelerSpawn> travelers,
        List<PropPlacement> props,
        List<String> barks,
        int despawnTicks
) {
    public record TravelerSpawn(ResourceLocation profile, int count) {
        public static final Codec<TravelerSpawn> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("profile").forGetter(TravelerSpawn::profile),
                Codec.INT.optionalFieldOf("count", 1).forGetter(TravelerSpawn::count)
        ).apply(instance, TravelerSpawn::new));
    }

    /// Offset is relative to the settlement's ARRIVAL anchor (falling back to its center).
    public record PropPlacement(Block block, BlockPos offset) {
        public static final Codec<PropPlacement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(PropPlacement::block),
                BlockPos.CODEC.fieldOf("offset").forGetter(PropPlacement::offset)
        ).apply(instance, PropPlacement::new));
    }

    public static final Codec<SituationDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TravelerSpawn.CODEC.listOf().optionalFieldOf("travelers", List.of()).forGetter(SituationDefinition::travelers),
            PropPlacement.CODEC.listOf().optionalFieldOf("props", List.of()).forGetter(SituationDefinition::props),
            Codec.STRING.listOf().optionalFieldOf("barks", List.of()).forGetter(SituationDefinition::barks),
            Codec.INT.optionalFieldOf("despawn_ticks", 24000).forGetter(SituationDefinition::despawnTicks)
    ).apply(instance, SituationDefinition::new));
}
