package dev.campaigncore.settlers.settlement;

import com.mojang.serialization.Codec;
import dev.campaigncore.settlers.SettlersMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/// The persistent registry of all settlements in a level (stored as SavedData on the overworld).
/// Also remembers which village structure starts were already converted or rolled-and-rejected, so
/// discovery never double-converts and never re-rolls a failed conversion chance.
public final class SettlementManager extends SavedData {
    private static final String DATA_NAME = "settlers_settlements";
    private static final Codec<List<Settlement>> SETTLEMENTS_CODEC = Settlement.CODEC.listOf();

    private final Map<UUID, Settlement> settlements = new HashMap<>();
    /// Structure-start chunk (packed) -> settlement id, for converted villages.
    private final Map<Long, UUID> convertedStarts = new HashMap<>();
    /// Structure-start chunks that failed the conversion roll — permanently ordinary villages.
    private final Set<Long> rejectedStarts = new HashSet<>();

    public static SettlementManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SettlementManager::new, SettlementManager::load, null),
                DATA_NAME);
    }

    private SettlementManager() {
    }

    private static SettlementManager load(CompoundTag tag, HolderLookup.Provider registries) {
        SettlementManager manager = new SettlementManager();
        SETTLEMENTS_CODEC.parse(NbtOps.INSTANCE, tag.get("settlements"))
                .resultOrPartial(error -> SettlersMod.LOGGER.error("Failed to load settlements: {}", error))
                .ifPresent(list -> list.forEach(settlement -> manager.settlements.put(settlement.id(), settlement)));
        CompoundTag converted = tag.getCompound("converted_starts");
        for (String key : converted.getAllKeys()) {
            try {
                manager.convertedStarts.put(Long.parseLong(key), converted.getUUID(key));
            } catch (NumberFormatException ignored) {
            }
        }
        for (long packed : tag.getLongArray("rejected_starts")) {
            manager.rejectedStarts.add(packed);
        }
        SettlersMod.LOGGER.info("Loaded {} settlements ({} converted starts, {} rejected).",
                manager.settlements.size(), manager.convertedStarts.size(), manager.rejectedStarts.size());
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        SETTLEMENTS_CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(this.settlements.values()))
                .resultOrPartial(error -> SettlersMod.LOGGER.error("Failed to save settlements: {}", error))
                .ifPresent(encoded -> tag.put("settlements", encoded));
        CompoundTag converted = new CompoundTag();
        this.convertedStarts.forEach((packed, id) -> converted.putUUID(String.valueOf(packed), id));
        tag.put("converted_starts", converted);
        tag.putLongArray("rejected_starts", this.rejectedStarts.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    // ---- Registration ----

    /// True if this start has been resolved either way (converted or rejected) — used by passive
    /// discovery, which must never re-roll or re-convert a start it's already handled.
    public boolean isStartTracked(ChunkPos startChunk) {
        long packed = startChunk.toLong();
        return this.convertedStarts.containsKey(packed) || this.rejectedStarts.contains(packed);
    }

    /// Unlike {@link #isStartTracked}, only true for an actual settlement — a rejected-but-not-yet-
    /// converted start is fine to force-convert. Use this (not isStartTracked) to guard the force
    /// -convert command, which exists specifically to bypass a failed conversion-chance roll.
    public boolean isConverted(ChunkPos startChunk) {
        return this.convertedStarts.containsKey(startChunk.toLong());
    }

    public void rejectStart(ChunkPos startChunk) {
        this.rejectedStarts.add(startChunk.toLong());
        this.setDirty();
    }

    /// A rejected start can still be force-converted by the dev command; clear the rejection first.
    public void unrejectStart(ChunkPos startChunk) {
        if (this.rejectedStarts.remove(startChunk.toLong())) {
            this.setDirty();
        }
    }

    public void register(ChunkPos startChunk, Settlement settlement) {
        this.settlements.put(settlement.id(), settlement);
        this.convertedStarts.put(startChunk.toLong(), settlement.id());
        this.setDirty();
    }

    // ---- Queries ----

    public Optional<Settlement> byId(UUID id) {
        return Optional.ofNullable(this.settlements.get(id));
    }

    public List<Settlement> all() {
        return new ArrayList<>(this.settlements.values());
    }

    public Optional<Settlement> at(BlockPos pos) {
        return this.settlements.values().stream().filter(settlement -> settlement.contains(pos)).findFirst();
    }

    public Optional<Settlement> nearest(BlockPos pos) {
        return this.settlements.values().stream()
                .min((a, b) -> Double.compare(a.center().distSqr(pos), b.center().distSqr(pos)));
    }

    /// State on settlements is mutated in place; call after any mutation so SavedData persists it.
    public void markDirty() {
        this.setDirty();
    }
}
