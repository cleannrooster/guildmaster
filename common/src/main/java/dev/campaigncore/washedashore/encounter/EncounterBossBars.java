package dev.campaigncore.washedashore.encounter;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transient boss bars for candidates flagged {@code boss_bar}. Not persisted: a bar is (re)opened lazily
 * while the encounter's tracked entity is alive and closed on completion/abort, so nothing survives — or
 * needs to survive — a reload beyond the encounter's own saved state.
 */
public final class EncounterBossBars {
    /** How near a player must be to see an encounter's boss bar. */
    private static final double VISIBLE_RADIUS = 48.0;
    private static final Map<EncounterAnchor, ServerBossEvent> BARS = new ConcurrentHashMap<>();

    private EncounterBossBars() {}

    /** Ensures a bar exists for {@code encounterId} with the given name; no-op if one is already open. */
    public static void open(EncounterAnchor encounter, Component name) {
        BARS.computeIfAbsent(encounter, key ->
                new ServerBossEvent(name, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
    }

    /**
     * Refreshes an open bar from the tracked entity: sets progress to its health fraction and shows the bar
     * to nearby players (hiding it from the rest). Closes the bar automatically if the entity is gone.
     */
    public static void updateFrom(ServerLevel level, EncounterAnchor encounter, UUID entityUuid) {
        ServerBossEvent bar = BARS.get(encounter);
        if (bar == null) return;
        Entity entity = entityUuid == null ? null : level.getEntity(entityUuid);
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            close(encounter);
            return;
        }
        bar.setProgress(Math.max(0f, Math.min(1f, living.getHealth() / living.getMaxHealth())));
        List<ServerPlayer> near = level.getPlayers(p -> p.distanceToSqr(living) <= VISIBLE_RADIUS * VISIBLE_RADIUS);
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            if (!near.contains(player)) bar.removePlayer(player);
        }
        for (ServerPlayer player : near) bar.addPlayer(player);
    }

    /** Whether a bar is currently open for the encounter (inspection only). */
    public static boolean isOpen(EncounterAnchor encounter) { return BARS.containsKey(encounter); }

    /** Removes and hides a bar (on completion, abort, or when its entity vanishes). */
    public static void close(EncounterAnchor encounter) {
        ServerBossEvent bar = BARS.remove(encounter);
        if (bar != null) bar.removeAllPlayers();
    }

    /** Clears transient bars when the owning server level unloads. */
    public static void closeAll() {
        for (ServerBossEvent bar : BARS.values()) bar.removeAllPlayers();
        BARS.clear();
    }
}
