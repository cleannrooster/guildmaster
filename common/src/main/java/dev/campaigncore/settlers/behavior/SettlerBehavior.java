package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.server.level.ServerLevel;

/// A reusable, data-consuming unit of Settler activity: move-to-anchor, sleep, work, socialize,
/// patrol, muster, flee. Behaviors never touch vanilla structure identity — they consume only the
/// settlement's anchor table and the settler's own home/work bindings, so the same behaviors work
/// unchanged once hand-authored structures register anchors themselves.
///
/// Every implementation must be resilient: an unreachable or missing anchor must make {@link #tick}
/// return {@code false} (never throw, never loop forever) so {@code ScheduleGoal} can fall back to
/// idle and re-evaluate, per the design's "schedules must not permanently strand an NPC" requirement.
public interface SettlerBehavior {
    /// Called once when this behavior becomes the active one.
    default void start(SettlerEntity settler, Settlement settlement) {
    }

    /// Called every tick while active. Return {@code false} to abandon the behavior (anchor missing,
    /// unreachable, or timed out) — control falls back to idle until the next schedule evaluation.
    boolean tick(SettlerEntity settler, Settlement settlement, ServerLevel level);

    /// Called once when this behavior is replaced or abandoned.
    default void stop(SettlerEntity settler) {
    }
}
