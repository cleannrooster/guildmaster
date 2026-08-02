package dev.campaigncore.settlers.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/// One planned or spawned resident on a settlement's roster. The roster is authored at conversion
/// (which profiles this settlement needs); `entityId` binds an entry to its live Settler once
/// population runs, so reloads re-link instead of respawning duplicates.
public final class ResidentEntry {
    public static final Codec<ResidentEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("profile").forGetter(entry -> entry.profile),
            UUIDUtil.CODEC.optionalFieldOf("entity_id").forGetter(entry -> Optional.ofNullable(entry.entityId)),
            BlockPos.CODEC.optionalFieldOf("home").forGetter(entry -> Optional.ofNullable(entry.home)),
            BlockPos.CODEC.optionalFieldOf("work").forGetter(entry -> Optional.ofNullable(entry.work)),
            // Optional and defaulting to empty so old saves without a work_type key still load; the
            // populator retrofits it from the resident's profile + the settlement anchor table.
            AnchorType.CODEC.optionalFieldOf("work_type").forGetter(entry -> Optional.ofNullable(entry.workType)),
            BlockPos.CODEC.optionalFieldOf("sleep").forGetter(entry -> Optional.ofNullable(entry.sleep)),
            ResourceLocation.CODEC.optionalFieldOf("job_offer").forGetter(entry -> Optional.ofNullable(entry.jobOffer))
    ).apply(instance, (profile, entityId, home, work, workType, sleep, jobOffer) ->
            new ResidentEntry(profile, entityId.orElse(null), home.orElse(null),
                    work.orElse(null), workType.orElse(null), sleep.orElse(null), jobOffer.orElse(null))));

    private ResourceLocation profile;
    private UUID entityId;
    private BlockPos home;
    private BlockPos work;
    private AnchorType workType;
    private BlockPos sleep;
    private ResourceLocation jobOffer;

    public ResidentEntry(ResourceLocation profile) {
        this(profile, null, null, null, null, null, null);
    }

    public ResidentEntry(ResourceLocation profile, UUID entityId, BlockPos home, BlockPos work,
                         AnchorType workType, BlockPos sleep) {
        this(profile, entityId, home, work, workType, sleep, null);
    }

    public ResidentEntry(ResourceLocation profile, UUID entityId, BlockPos home, BlockPos work,
                         AnchorType workType, BlockPos sleep, ResourceLocation jobOffer) {
        this.profile = profile;
        this.entityId = entityId;
        this.home = home;
        this.work = work;
        this.workType = workType;
        this.sleep = sleep;
        this.jobOffer = jobOffer;
    }

    public ResourceLocation profile() {
        return this.profile;
    }

    public void setProfile(ResourceLocation profile) {
        this.profile = profile;
    }

    public Optional<ResourceLocation> jobOffer() {
        return Optional.ofNullable(this.jobOffer);
    }

    public void setJobOffer(ResourceLocation jobOffer) {
        this.jobOffer = jobOffer;
    }

    public void clearJobOffer() {
        this.jobOffer = null;
    }

    public Optional<UUID> entityId() {
        return Optional.ofNullable(this.entityId);
    }

    public void bind(UUID entityId) {
        this.entityId = entityId;
    }

    /// Clears the live-entity binding (the resident died); the next population pass spawns a
    /// replacement into this slot, inheriting its home/work/sleep anchors.
    public void unbind() {
        this.entityId = null;
    }

    public Optional<BlockPos> home() {
        return Optional.ofNullable(this.home);
    }

    public void setHome(BlockPos home) {
        this.home = home;
    }

    public void clearHome() {
        this.home = null;
    }

    public Optional<BlockPos> work() {
        return Optional.ofNullable(this.work);
    }

    public Optional<AnchorType> workType() {
        return Optional.ofNullable(this.workType);
    }

    /// Binds this resident to a workstation: both the anchor type (which production consults to route
    /// this resident to a {@link dev.campaigncore.settlers.production.ProductionProcess}) and the concrete position.
    public void setWork(AnchorType type, BlockPos pos) {
        this.workType = type;
        this.work = pos;
    }

    /// Clears the work binding so the next population pass re-round-robins it (see the repopulate
    /// command). Both the type and the position are dropped together.
    public void clearWork() {
        this.workType = null;
        this.work = null;
    }

    public Optional<BlockPos> sleep() {
        return Optional.ofNullable(this.sleep);
    }

    public void setSleep(BlockPos sleep) {
        this.sleep = sleep;
    }

    public void clearSleep() {
        this.sleep = null;
    }
}
