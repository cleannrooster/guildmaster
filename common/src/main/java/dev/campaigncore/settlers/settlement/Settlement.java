package dev.campaigncore.settlers.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.campaigncore.settlers.economy.SettlementEconomyState;
import dev.campaigncore.settlers.production.ProductionState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// One persistent settlement: identity, profile, spatial data (bounds/center/structures/anchors),
/// roster, and live state (threat, situation, condition). Owned and serialized by
/// {@link SettlementManager}; never reconstructed from entity scans.
public final class Settlement {
    public static final Codec<Settlement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(s -> s.id),
            SettlementProfile.CODEC.fieldOf("profile").forGetter(s -> s.profile),
            BoundingBox.CODEC.fieldOf("bounds").forGetter(s -> s.bounds),
            BlockPos.CODEC.fieldOf("center").forGetter(s -> s.center),
            StructureAssignment.CODEC.listOf().fieldOf("structures").forGetter(s -> s.structures),
            AnchorTable.CODEC.fieldOf("anchors").forGetter(s -> s.anchors),
            ResidentEntry.CODEC.listOf().fieldOf("roster").forGetter(s -> s.roster),
            ThreatState.CODEC.optionalFieldOf("threat_state", ThreatState.NORMAL).forGetter(s -> s.threatState),
            // Optional with a shared EMPTY default so pre-production saves load cleanly; the constructor
            // copies it into a fresh instance, so settlements never share the sentinel.
            ProductionState.CODEC.optionalFieldOf("production", ProductionState.EMPTY).forGetter(s -> s.production),
            // Same optional-with-copied-default pattern for the economy layer added on top of production.
            SettlementEconomyState.CODEC.optionalFieldOf("economy", SettlementEconomyState.EMPTY).forGetter(s -> s.economy)
            , com.mojang.serialization.Codec.BOOL.optionalFieldOf("anchors_dirty", false).forGetter(s -> s.anchorsDirty)
            , com.mojang.serialization.Codec.LONG.optionalFieldOf("next_anchor_audit", 0L).forGetter(s -> s.nextAnchorAuditGameTime)
            , ChronicleEntry.CODEC.listOf().optionalFieldOf("chronicle", List.of()).forGetter(s -> s.chronicle)
    ).apply(instance, Settlement::new));

    private final UUID id;
    private SettlementProfile profile;
    private  BoundingBox bounds;
    private final BlockPos center;
    private final List<StructureAssignment> structures;
    private final AnchorTable anchors;
    private final List<ResidentEntry> roster;
    private ThreatState threatState;
    private final ProductionState production;
    private final SettlementEconomyState economy;
    private boolean anchorsDirty;
    private long anchorsRebuildAfterGameTime;
    private long nextAnchorAuditGameTime;
    private final List<ChronicleEntry> chronicle;
    public static final int MAX_CHRONICLE_ENTRIES = 128;
    // Runtime-only (not persisted): a reload mid-recovery just re-enters RECOVERY and restarts the
    // countdown next threat tick, which is an acceptable beta shortcut per the design's tolerance for
    // documented shortcuts in non-critical state.
    private long recoveryEndsAt;
    // Runtime attack-state hold. A reload during an attack starts a fresh minimum hold, preferring a
    // slightly longer alarm over allowing UNDER_ATTACK/PANIC to collapse immediately after loading.
    private long attackStateEndsAt;

    public Settlement(UUID id, SettlementProfile profile, BoundingBox bounds, BlockPos center,
                      List<StructureAssignment> structures, AnchorTable anchors, List<ResidentEntry> roster,
                      ThreatState threatState, ProductionState production, SettlementEconomyState economy) {
        this(id, profile, bounds, center, structures, anchors, roster, threatState, production, economy,
                false, 0L, List.of());
    }

    public Settlement(UUID id, SettlementProfile profile, BoundingBox bounds, BlockPos center,
                      List<StructureAssignment> structures, AnchorTable anchors, List<ResidentEntry> roster,
                      ThreatState threatState, ProductionState production, SettlementEconomyState economy,
                      boolean anchorsDirty, long nextAnchorAuditGameTime) {
        this(id, profile, bounds, center, structures, anchors, roster, threatState, production, economy,
                anchorsDirty, nextAnchorAuditGameTime, List.of());
    }

    public Settlement(UUID id, SettlementProfile profile, BoundingBox bounds, BlockPos center,
                      List<StructureAssignment> structures, AnchorTable anchors, List<ResidentEntry> roster,
                      ThreatState threatState, ProductionState production, SettlementEconomyState economy,
                      boolean anchorsDirty, long nextAnchorAuditGameTime, List<ChronicleEntry> chronicle) {
        this.id = id;
        this.profile = profile;
        this.bounds = bounds;
        this.center = center;
        this.structures = new ArrayList<>(structures);
        this.anchors = anchors;
        this.roster = new ArrayList<>(roster);
        this.threatState = threatState;
        // Copy so each settlement owns its own state even when the codec hands back the shared EMPTY.
        this.production = new ProductionState(production);
        this.economy = new SettlementEconomyState(economy);
        this.anchorsDirty = anchorsDirty;
        this.nextAnchorAuditGameTime = nextAnchorAuditGameTime;
        this.chronicle = new ArrayList<>(chronicle);
    }

    public UUID id() {
        return this.id;
    }

    public SettlementProfile profile() {
        return this.profile;
    }

    public String name() {
        return this.profile.name();
    }

    public String currentSituation() {
        return this.profile.currentSituation();
    }

    public void setCurrentSituation(String situation) {
        this.profile = this.profile.withCurrentSituation(situation);
    }

    public BoundingBox bounds() {
        return this.bounds;
    }

    public BlockPos center() {
        return this.center;
    }

    public List<StructureAssignment> structures() {
        return this.structures;
    }

    public AnchorTable anchors() {
        return this.anchors;
    }

    public List<ResidentEntry> roster() {
        return this.roster;
    }

    public ThreatState threatState() {
        return this.threatState;
    }

    public ProductionState production() {
        return this.production;
    }

    public SettlementEconomyState economy() {
        return this.economy;
    }

    public List<ChronicleEntry> chronicle() {
        return java.util.Collections.unmodifiableList(this.chronicle);
    }

    public void recordChronicle(long gameTime, String event, String subject) {
        this.chronicle.add(new ChronicleEntry(gameTime, event, subject));
        while (this.chronicle.size() > MAX_CHRONICLE_ENTRIES) {
            this.chronicle.removeFirst();
        }
    }

    public boolean anchorsDirty() {
        return this.anchorsDirty;
    }

    public long anchorsRebuildAfterGameTime() {
        return this.anchorsRebuildAfterGameTime;
    }

    public long nextAnchorAuditGameTime() {
        return this.nextAnchorAuditGameTime;
    }

    public void markAnchorsDirty(long rebuildAfterGameTime) {
        this.anchorsDirty = true;
        this.anchorsRebuildAfterGameTime = Math.max(this.anchorsRebuildAfterGameTime, rebuildAfterGameTime);
    }

    public void completeAnchorRebuild(long nextAuditGameTime) {
        this.anchorsDirty = false;
        this.anchorsRebuildAfterGameTime = 0L;
        this.nextAnchorAuditGameTime = nextAuditGameTime;
    }

    public void scheduleAnchorAudit(long gameTime) {
        this.nextAnchorAuditGameTime = gameTime;
    }
    public void addStructures(
            List<StructureAssignment> added
    ) {
        for (StructureAssignment assignment : added) {
            this.structures.add(assignment);
            // encapsulate returns a new box; it does not mutate in place, so the result must be
            // reassigned or the settlement bounds never grow to cover the expansion.
            this.bounds = this.bounds.encapsulate(assignment.bounds());
        }
    }
    public void setThreatState(ThreatState state) {
        this.threatState = state;
    }

    public void startRecovery(long currentGameTime, int durationTicks) {
        this.recoveryEndsAt = currentGameTime + durationTicks;
    }

    public boolean recoveryComplete(long currentGameTime) {
        return currentGameTime >= this.recoveryEndsAt;
    }

    public void startAttackState(long currentGameTime, int minimumDurationTicks) {
        if (this.attackStateEndsAt == 0L) {
            this.attackStateEndsAt = currentGameTime + minimumDurationTicks;
        }
    }

    public boolean attackStateMinimumComplete(long currentGameTime) {
        return this.attackStateEndsAt > 0L && currentGameTime >= this.attackStateEndsAt;
    }

    public void clearAttackState() {
        this.attackStateEndsAt = 0L;
    }

    public boolean contains(BlockPos pos) {
        // Villages are surface communities; accept a generous vertical band around the structure box so
        // hilltop houses and cellar interiors still count as "inside".
        return pos.getX() >= this.bounds.minX() - 8 && pos.getX() <= this.bounds.maxX() + 8
                && pos.getZ() >= this.bounds.minZ() - 8 && pos.getZ() <= this.bounds.maxZ() + 8
                && pos.getY() >= this.bounds.minY() - 16 && pos.getY() <= this.bounds.maxY() + 32;
    }
}
