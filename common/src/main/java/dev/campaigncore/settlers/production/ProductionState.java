package dev.campaigncore.settlers.production;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/// A settlement's accumulated economic state: how much of each abstract resource it has stored, how
/// far each production process has progressed toward its next completed cycle, and the game time its
/// production was last advanced to (for elapsed-time accounting in
/// {@link SettlementProductionTicker}).
///
/// Mutable and owned per-settlement — {@link dev.campaigncore.settlers.settlement.Settlement} copies whatever the
/// codec hands it (including the shared {@link #EMPTY} default) into a fresh instance, so no two
/// settlements ever share one of these.
public final class ProductionState {
    /// The default handed to settlements whose save predates production. It is only ever *copied*
    /// (never mutated in place) — see the Settlement constructor — so sharing this single instance as a
    /// codec default is safe.
    public static final ProductionState EMPTY = new ProductionState();

    private static final Codec<Map<ResourceLocation, Double>> DOUBLE_MAP_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE);

    public static final Codec<ProductionState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DOUBLE_MAP_CODEC.optionalFieldOf("stored", Map.of()).forGetter(state -> state.storedResources),
            DOUBLE_MAP_CODEC.optionalFieldOf("progress", Map.of()).forGetter(state -> state.fractionalProgress),
            Codec.LONG.optionalFieldOf("last_processed", 0L).forGetter(state -> state.lastProcessedGameTime)
    ).apply(instance, ProductionState::new));

    private final Map<ResourceLocation, Double> storedResources;
    private final Map<ResourceLocation, Double> fractionalProgress;
    private long lastProcessedGameTime;

    public ProductionState() {
        this.storedResources = new HashMap<>();
        this.fractionalProgress = new HashMap<>();
        this.lastProcessedGameTime = 0L;
    }

    /// Canonical constructor used by the codec: defensively copies the maps so decoded state never
    /// aliases a shared/immutable map (e.g. the {@code Map.of()} defaults).
    public ProductionState(Map<ResourceLocation, Double> stored, Map<ResourceLocation, Double> progress,
                           long lastProcessedGameTime) {
        this.storedResources = new HashMap<>(stored);
        this.fractionalProgress = new HashMap<>(progress);
        this.lastProcessedGameTime = lastProcessedGameTime;
    }

    /// Deep copy — used by the Settlement constructor to give each settlement its own owned state.
    public ProductionState(ProductionState other) {
        this(other.storedResources, other.fractionalProgress, other.lastProcessedGameTime);
    }

    public double stored(ResourceLocation resource) {
        return this.storedResources.getOrDefault(resource, 0.0);
    }

    public void addStored(ResourceLocation resource, double amount) {
        checkAmount(amount);
        if (amount == 0.0) {
            return;
        }
        this.storedResources.merge(resource, amount, Double::sum);
    }

    /// Subtracts up to {@code amount} of a resource, never below zero, and returns the amount actually
    /// consumed. The economy consumes food through here; raw-balance mutation belongs on this class, not
    /// on the economy state (which keeps food authoritative in {@link #stored}).
    public double consumeStored(ResourceLocation resource, double amount) {
        checkAmount(amount);
        double available = stored(resource);
        double consumed = Math.min(amount, available);
        if (consumed <= 0.0) {
            return 0.0;
        }
        double remaining = available - consumed;
        if (remaining <= 1.0e-9) {
            this.storedResources.remove(resource);
        } else {
            this.storedResources.put(resource, remaining);
        }
        return consumed;
    }

    public double progress(ResourceLocation process) {
        return this.fractionalProgress.getOrDefault(process, 0.0);
    }

    public void addProgress(ResourceLocation process, double amount) {
        checkAmount(amount);
        if (amount == 0.0) {
            return;
        }
        this.fractionalProgress.merge(process, amount, Double::sum);
    }

    /// Removes and returns the whole completed cycles accumulated for a process, leaving the sub-cycle
    /// remainder behind. E.g. progress 2.7 -> returns 2.0, leaves 0.7.
    public double consumeProgressUnits(ResourceLocation process) {
        double current = this.fractionalProgress.getOrDefault(process, 0.0);
        double whole = Math.floor(current);
        if (whole <= 0.0) {
            return 0.0;
        }
        this.fractionalProgress.put(process, current - whole);
        return whole;
    }

    public long lastProcessedGameTime() {
        return this.lastProcessedGameTime;
    }

    public void setLastProcessedGameTime(long time) {
        this.lastProcessedGameTime = time;
    }

    private static void checkAmount(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("Production amounts must be finite and non-negative: " + amount);
        }
    }
}
