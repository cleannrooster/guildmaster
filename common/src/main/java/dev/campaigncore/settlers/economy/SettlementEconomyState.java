package dev.campaigncore.settlers.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// Persistent economy bookkeeping for a settlement, kept separate from the raw production accumulator
/// ({@link dev.campaigncore.settlers.production.ProductionState}). Food itself stays authoritative in production's
/// stored {@code settlers:food}; this class holds only what production has no concept of: outstanding
/// food debt, the count of player-claimable provision bundles, and the timestamps that drive
/// elapsed-time consumption and once-per-day conversion.
///
/// Mutable and owned per-settlement — {@link dev.campaigncore.settlers.settlement.Settlement} copies whatever the
/// codec hands it (including the shared {@link #EMPTY} default) into a fresh instance, so no two
/// settlements ever share one.
public final class SettlementEconomyState {
    /// Storage ceiling for claimable bundles; enforced by {@link #addBundles} and {@link #setBundles}.
    public static final int MAX_PROVISION_BUNDLES_STORED = 8;

    /// The default handed to settlements whose save predates the economy. Only ever *copied* (never
    /// mutated in place) — see the Settlement constructor — so sharing it as a codec default is safe.
    public static final SettlementEconomyState EMPTY = new SettlementEconomyState();

    public static final Codec<SettlementEconomyState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("food_debt", 0.0).forGetter(s -> s.foodDebt),
            Codec.INT.optionalFieldOf("claimable_provision_bundles", 0).forGetter(s -> s.claimableProvisionBundles),
            Codec.LONG.optionalFieldOf("last_consumption_game_time", 0L).forGetter(s -> s.lastConsumptionGameTime),
            Codec.LONG.optionalFieldOf("last_conversion_day", 0L).forGetter(s -> s.lastConversionDay)
    ).apply(instance, SettlementEconomyState::new));

    private double foodDebt;
    private int claimableProvisionBundles;
    private long lastConsumptionGameTime;
    private long lastConversionDay;

    public SettlementEconomyState() {
    }

    /// Canonical constructor used by the codec.
    public SettlementEconomyState(double foodDebt, int claimableProvisionBundles,
                                  long lastConsumptionGameTime, long lastConversionDay) {
        this.foodDebt = Math.max(0.0, foodDebt);
        this.claimableProvisionBundles = clampBundles(claimableProvisionBundles);
        this.lastConsumptionGameTime = lastConsumptionGameTime;
        this.lastConversionDay = lastConversionDay;
    }

    /// Deep copy — used by the Settlement constructor to give each settlement its own owned state.
    public SettlementEconomyState(SettlementEconomyState other) {
        this(other.foodDebt, other.claimableProvisionBundles, other.lastConsumptionGameTime, other.lastConversionDay);
    }

    // ---- Food debt ----

    public double foodDebt() {
        return this.foodDebt;
    }

    public void addDebt(double amount) {
        checkAmount(amount);
        this.foodDebt += amount;
    }

    /// Reduces debt by up to {@code amount} (never below zero) and returns how much was actually reduced.
    public double reduceDebt(double amount) {
        checkAmount(amount);
        double reduced = Math.min(amount, this.foodDebt);
        this.foodDebt -= reduced;
        return reduced;
    }

    // ---- Claimable provision bundles ----

    public int claimableProvisionBundles() {
        return this.claimableProvisionBundles;
    }

    /// Adds bundles, saturating at {@link #MAX_PROVISION_BUNDLES_STORED}. Returns the number actually
    /// added (fewer than requested if the cap was hit).
    public int addBundles(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Bundle count must be non-negative: " + count);
        }
        int before = this.claimableProvisionBundles;
        this.claimableProvisionBundles = clampBundles(before + count);
        return this.claimableProvisionBundles - before;
    }

    /// Removes up to {@code count} bundles and returns how many were actually claimed.
    public int claimBundles(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Bundle count must be non-negative: " + count);
        }
        int claimed = Math.min(count, this.claimableProvisionBundles);
        this.claimableProvisionBundles -= claimed;
        return claimed;
    }

    public void setBundles(int count) {
        this.claimableProvisionBundles = clampBundles(count);
    }

    // ---- Timestamps ----

    public long lastConsumptionGameTime() {
        return this.lastConsumptionGameTime;
    }

    public void setLastConsumptionGameTime(long time) {
        this.lastConsumptionGameTime = time;
    }

    public long lastConversionDay() {
        return this.lastConversionDay;
    }

    public void setLastConversionDay(long day) {
        this.lastConversionDay = day;
    }

    private static int clampBundles(int count) {
        return Math.max(0, Math.min(MAX_PROVISION_BUNDLES_STORED, count));
    }

    private static void checkAmount(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("Economy amounts must be finite and non-negative: " + amount);
        }
    }
}
