package dev.campaigncore.settlers.situation;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.config.SettlersConfig;
import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.entity.data.SettlerDataManager;
import dev.campaigncore.settlers.registry.SettlersEntities;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;

/// Stages a settlement's current situation: temporary travelers, decorative props near the arrival
/// point, and an ambient bark to nearby players. Deliberately theatrical per the design — no quest
/// chain, no simulated caravan inventory, just staging that makes "something is happening here"
/// legible on arrival.
public final class SituationRunner {
    private static final double BARK_RADIUS = 48.0;

    private SituationRunner() {
    }

    public static void trigger(ServerLevel level, Settlement settlement) {
        SituationManager.byName(settlement.currentSituation()).ifPresentOrElse(
                situation -> apply(level, settlement, situation),
                () -> SettlersMod.LOGGER.info("Settlement '{}' situation '{}' has no matching definition; skipping staging.",
                        settlement.name(), settlement.currentSituation()));
    }

    private static void apply(ServerLevel level, Settlement settlement, SituationDefinition situation) {
        BlockPos origin = settlement.anchors().first(AnchorType.ARRIVAL).orElse(settlement.center());

        placeProps(level, origin, situation);
        spawnTravelers(level, settlement, origin, situation);
        bark(level, settlement, situation);

        SettlersMod.LOGGER.info("Staged situation '{}' for settlement '{}' at {}.",
                settlement.currentSituation(), settlement.name(), origin);
    }

    /// Only places into currently non-solid space, so staging never overwrites terrain or a player's
    /// own building — it's decoration, not guaranteed-successful set dressing.
    private static void placeProps(ServerLevel level, BlockPos origin, SituationDefinition situation) {
        for (SituationDefinition.PropPlacement prop : situation.props()) {
            BlockPos pos = origin.offset(prop.offset());
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState existing = level.getBlockState(pos);
            if (!existing.isAir() && !existing.getCollisionShape(level, pos).isEmpty()) {
                continue;
            }
            level.setBlockAndUpdate(pos, prop.block().defaultBlockState());
        }
    }

    private static void spawnTravelers(ServerLevel level, Settlement settlement, BlockPos origin, SituationDefinition situation) {
        if (!SettlersConfig.get().population.enableTravelers) {
            return;
        }
        RandomSource random = level.getRandom();
        for (SituationDefinition.TravelerSpawn spawn : situation.travelers()) {
            if (SettlerDataManager.profile(spawn.profile()).isEmpty()) {
                SettlersMod.LOGGER.warn("Situation traveler profile {} not found; skipping.", spawn.profile());
                continue;
            }
            for (int i = 0; i < spawn.count(); i++) {
                SettlerEntity traveler = SettlersEntities.SETTLER.get().create(level);
                if (traveler == null) {
                    continue;
                }
                BlockPos pos = origin.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                traveler.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
                traveler.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
                traveler.applyProfile(spawn.profile());
                traveler.bindSettlement(settlement.id());
                traveler.setHomeAnchor(origin);
                traveler.setDespawnTimer(situation.despawnTicks());
                level.addFreshEntity(traveler);
            }
        }
    }

    private static void bark(ServerLevel level, Settlement settlement, SituationDefinition situation) {
        if (situation.barks().isEmpty()) {
            return;
        }
        String message = situation.barks().get(level.getRandom().nextInt(situation.barks().size()))
                .replace("{settlement}", settlement.name());
        Component text = Component.literal(message);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(settlement.center()) <= BARK_RADIUS * BARK_RADIUS) {
                player.sendSystemMessage(text);
            }
        }
    }
}
