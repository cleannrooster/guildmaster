package dev.campaigncore.settlers.detection;

import dev.campaigncore.settlers.config.SettlersConfig;
import dev.campaigncore.settlers.settlement.SettlementManager;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

/// Config-gated: blocks vanilla villagers from (re)appearing inside converted settlement bounds —
/// wandering in, breeding, curing, or spawning from eggs. Villages everywhere else are untouched.
public final class VillagerSuppressor {
    private VillagerSuppressor() {
    }

    public static void register() {
        EntityEvent.ADD.register(VillagerSuppressor::onEntityAdd);
    }

    private static EventResult onEntityAdd(Entity entity, Level level) {
        if (!(entity instanceof Villager) || !(level instanceof ServerLevel serverLevel)) {
            return EventResult.pass();
        }
        if (!SettlersConfig.get().conversion.removeVillagersFromConvertedSettlements) {
            return EventResult.pass();
        }
        if (SettlementManager.get(serverLevel).at(entity.blockPosition()).isPresent()) {
            return EventResult.interruptFalse();
        }
        return EventResult.pass();
    }
}
