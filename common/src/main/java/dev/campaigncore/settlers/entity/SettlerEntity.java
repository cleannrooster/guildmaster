package dev.campaigncore.settlers.entity;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.behavior.ScheduleGoal;
import dev.campaigncore.settlers.behavior.SettlerAttackTargetGoal;
import dev.campaigncore.settlers.behavior.SettlerHurtByTargetGoal;
import dev.campaigncore.settlers.entity.data.SettlerDataManager;
import dev.campaigncore.settlers.entity.data.SettlerProfile;
import dev.campaigncore.settlers.economy.SettlementEconomyModel;
import dev.campaigncore.settlers.economy.SettlementEconomyReport;
import dev.campaigncore.settlers.entity.data.SettlerRole;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.SettlementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.UUID;

/// The single Settler entity class. All variation — role, profession, faction, texture, equipment,
/// and later schedule and behavior — comes from the datapack {@link SettlerProfile} this entity is
/// assigned, never from subclassing.
///
/// The profile id and skin texture are synced so the client can render without needing datapack
/// access; equipment syncs through the vanilla equipment pipeline.
public class SettlerEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> DATA_PROFILE =
            SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.STRING);
    // Empty string is the "not yet assigned" sentinel for both of these — see assignBaseSkinIfNeeded.
    private static final EntityDataAccessor<String> DATA_TEXTURE =
            SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_JOB_OVERLAY =
            SynchedEntityData.defineId(SettlerEntity.class, EntityDataSerializers.STRING);

    public static final ResourceLocation DEFAULT_PROFILE =
            ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "civilian");

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.settler.idle");
    private static final String WORK_HAMMER_ANIM = "work_hammer";

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private SettlerRole role = SettlerRole.CIVILIAN;

    // Settlement membership and per-resident anchor bindings. Server-side only — behaviors read these
    // directly, no sync needed. Persisted so a Settler stays bound to its settlement/anchors across
    // reloads without re-running population.
    @Nullable
    private UUID settlementId;
    @Nullable
    private BlockPos homeAnchor;
    @Nullable
    private BlockPos workAnchor;
    @Nullable
    private AnchorType workAnchorType;
    @Nullable
    private BlockPos sleepAnchor;
    // -1 = persistent resident (default). Positive = ticks remaining before this Settler (a traveler)
    // departs on its own; see SituationRunner.
    private int despawnTicksRemaining = -1;

    // Runtime-only provision-delivery errand (a reeve dispatched to hand bundles to a player). Not
    // persisted: a restart mid-walk simply drops the errand, and the bundles — never decremented until
    // hand-off — stay claimable for the player to request again. See DeliverProvisionsGoal.
    @Nullable
    private UUID deliveryTargetPlayer;
    private long deliveryDeadlineTick;
    @Nullable
    private UUID conversationPartner;
    private long conversationUntilTick;

    public SettlerEntity(EntityType<? extends SettlerEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        // Higher priority than the schedule so a live combat target preempts patrol/muster/work.
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.28, false));
        // A dispatched reeve walking provisions to a player preempts the normal schedule but yields to
        // combat (its canUse requires no attack target). Only reeves ever receive a delivery request.
        // Settlement-bound settlers follow their schedule; unbound settlers (dev-spawned, or
        // mid-population) fall through to the vanilla wander/look goals below so they're never frozen.
        this.goalSelector.addGoal(3, new ScheduleGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Every settler defends itself if struck; only guards proactively hunt nearby hostiles (role
        // is checked live inside SettlerAttackTargetGoal since it's set post-construction).
        this.targetSelector.addGoal(1, new SettlerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new SettlerAttackTargetGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PROFILE, "");
        builder.define(DATA_TEXTURE, "");
        builder.define(DATA_JOB_OVERLAY, "");
    }
    @Override
    protected GroundPathNavigation createNavigation(
            net.minecraft.world.level.Level level
    ) {
        GroundPathNavigation navigation =
                new GroundPathNavigation(this, level);

        navigation.setCanOpenDoors(true);
        navigation.setCanPassDoors(true);

        return navigation;
    }
    // ---- Profile ----

    /// Assigns a datapack profile: role, skin, name, and equipment. Missing profile ids fail soft to a
    /// default-textured generic civilian (logged) — a broken datapack must never crash a settlement.
    public void applyProfile(ResourceLocation profileId) {
        Optional<SettlerProfile> found = SettlerDataManager.profile(profileId);
        if (found.isEmpty()) {
            SettlersMod.LOGGER.warn("Settler profile {} not found; {} falls back to generic civilian.",
                    profileId, this.getUUID());
            this.entityData.set(DATA_PROFILE, profileId.toString());
            this.assignBaseSkinIfNeeded(Optional.empty());
            return;
        }
        SettlerProfile profile = found.get();
        this.entityData.set(DATA_PROFILE, profileId.toString());
        this.role = profile.role();
        this.assignBaseSkinIfNeeded(profile.texture());
        this.entityData.set(DATA_JOB_OVERLAY, profile.jobOverlay().map(ResourceLocation::toString).orElse(""));
        profile.displayName().ifPresent(name -> this.setCustomName(Component.literal(name)));
        profile.equipmentProfile().ifPresent(equipmentId ->
                SettlerDataManager.equipment(equipmentId).ifPresentOrElse(
                        equipment -> equipment.apply(this),
                        () -> SettlersMod.LOGGER.warn("Equipment profile {} (for settler profile {}) not found.",
                                equipmentId, profileId)));
    }

    /// The base skin is chosen once and persists for this entity's lifetime, independent of
    /// profession — an explicit profile texture override always wins (re-applied every time, so it
    /// tracks profile changes), but otherwise a random pick from the base skin pool only happens the
    /// first time (an empty synced value is the "not yet assigned" sentinel) and is never re-rolled.
    private void assignBaseSkinIfNeeded(Optional<ResourceLocation> explicitTexture) {
        if (explicitTexture.isPresent()) {
            this.entityData.set(DATA_TEXTURE, explicitTexture.get().toString());
            return;
        }
        if (!this.entityData.get(DATA_TEXTURE).isEmpty()) {
            return;
        }
        SettlerDataManager.randomBaseSkin(this.getRandom())
                .ifPresent(texture -> this.entityData.set(DATA_TEXTURE, texture.toString()));
    }

    public Optional<ResourceLocation> profileId() {
        String raw = this.entityData.get(DATA_PROFILE);
        return raw.isEmpty() ? Optional.empty() : Optional.ofNullable(ResourceLocation.tryParse(raw));
    }

    public SettlerRole role() {
        return this.role;
    }

    // ---- Settlement membership ----

    public void bindSettlement(UUID settlementId) {
        this.settlementId = settlementId;
    }

    public Optional<UUID> settlementId() {
        return Optional.ofNullable(this.settlementId);
    }

    public void setHomeAnchor(BlockPos pos) {
        this.homeAnchor = pos;
    }

    public Optional<BlockPos> homeAnchor() {
        return Optional.ofNullable(this.homeAnchor);
    }

    public void setWorkAnchor(AnchorType type, BlockPos pos) {
        this.workAnchorType = type;
        this.workAnchor = pos;
    }

    public Optional<BlockPos> workAnchor() {
        return Optional.ofNullable(this.workAnchor);
    }

    public Optional<AnchorType> workAnchorType() {
        return Optional.ofNullable(this.workAnchorType);
    }

    public void setSleepAnchor(BlockPos pos) {
        this.sleepAnchor = pos;
    }

    public Optional<BlockPos> sleepAnchor() {
        return Optional.ofNullable(this.sleepAnchor);
    }

    // ---- Provision-delivery errand (runtime-only) ----

    /// Dispatches this settler (a reeve) to deliver its settlement's claimable bundles to a player,
    /// giving up after {@code deadlineTick}. See {@link DeliverProvisionsGoal}.
    @Deprecated(forRemoval = true)
    public void setDeliveryRequest(UUID playerId, long deadlineTick) {
        this.deliveryTargetPlayer = playerId;
        this.deliveryDeadlineTick = deadlineTick;
    }

    @Deprecated(forRemoval = true)
    public boolean hasDeliveryRequest() {
        return this.deliveryTargetPlayer != null;
    }

    @Deprecated(forRemoval = true)
    public Optional<UUID> deliveryTargetPlayer() {
        return Optional.ofNullable(this.deliveryTargetPlayer);
    }

    @Deprecated(forRemoval = true)
    public long deliveryDeadlineTick() {
        return this.deliveryDeadlineTick;
    }

    @Deprecated(forRemoval = true)
    public void clearDelivery() {
        this.deliveryTargetPlayer = null;
        this.deliveryDeadlineTick = 0L;
    }

    // ---- Short, runtime-only social conversations ----

    public void beginConversation(UUID partnerId, long untilTick) {
        this.conversationPartner = partnerId;
        this.conversationUntilTick = untilTick;
    }

    public Optional<UUID> conversationPartner() {
        return Optional.ofNullable(this.conversationPartner);
    }

    public boolean hasActiveConversation(long gameTime) {
        return this.conversationPartner != null && gameTime < this.conversationUntilTick;
    }

    public void clearConversation() {
        this.conversationPartner = null;
        this.conversationUntilTick = 0L;
    }

    /// Fires the authored work animation on the "action" controller layer. Server call; GeckoLib
    /// networks the trigger to tracking clients.
    public void triggerWorkAnimation() {
        this.triggerAnim("action", WORK_HAMMER_ANIM);
    }

    /// Client-side: the synced player-format base skin for this Settler (one of the random pool, or
    /// an explicit profile override).
    public ResourceLocation textureLocation() {
        ResourceLocation parsed = ResourceLocation.tryParse(this.entityData.get(DATA_TEXTURE));
        return parsed != null ? parsed : SettlerProfile.DEFAULT_TEXTURE;
    }

    /// Client-side: the job-identifying overlay texture rendered on top of the base skin, if this
    /// profile has one.
    public Optional<ResourceLocation> jobOverlayTexture() {
        String raw = this.entityData.get(DATA_JOB_OVERLAY);
        return raw.isEmpty() ? Optional.empty() : Optional.ofNullable(ResourceLocation.tryParse(raw));
    }

    // ---- Interaction ----

    /// Right-click behavior. A reeve bound to a settlement reports that settlement's economy status
    /// (the read-only counterpart to the Ledger's delivery flow); every other settler falls back to the
    /// identity line (name, profession, settlement). Read-only — nothing is mutated here.
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide || hand != InteractionHand.MAIN_HAND) {
            return super.mobInteract(player, hand);
        }
        String profession = this.profileId()
                .flatMap(SettlerDataManager::profile)
                .map(SettlerProfile::profession)
                .orElse("laborer");
        ServerLevel level = (ServerLevel) this.level();
        Optional<Settlement> settlement = this.settlementId()
                .flatMap(id -> SettlementManager.get(level).byId(id));

        if (profession.equals("reeve") && settlement.isPresent()) {
            sendEconomyStatus(player, level, settlement.get());
            return InteractionResult.SUCCESS;
        }

        Component settlementName = settlement.map(s -> (Component) Component.literal(s.name()))
                .orElseGet(() -> Component.translatable("settlers.settler.no_settlement"));
        Component name = this.hasCustomName() ? this.getCustomName()
                : Component.translatable("settlers.settler.generic");
        player.sendSystemMessage(Component.translatable("settlers.settler.identify", name, profession, settlementName));
        return InteractionResult.SUCCESS;
    }

    private static void sendEconomyStatus(Player player, ServerLevel level, Settlement settlement) {
        SettlementEconomyReport report = SettlementEconomyModel.report(level, settlement);
        player.sendSystemMessage(Component.translatable("settlers.reeve.status.header", settlement.name())
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("settlers.reeve.status.condition",
                report.condition().getSerializedName(), report.population()));
        player.sendSystemMessage(Component.translatable("settlers.reeve.status.production",
                fmt(report.dailyFoodProduction()), fmt(report.dailyFoodConsumption())));
        player.sendSystemMessage(Component.translatable("settlers.reeve.status.surplus",
                signed(report.projectedDailySurplus()), fmt(report.foodStorageReserve()),
                fmt(report.storedFood()), fmt(report.foodDebt())));
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String signed(double value) {
        return (value >= 0.0 ? "+" : "") + fmt(value);
    }

    // ---- Travelers ----

    /// Marks this Settler as a temporary visitor: it discards itself after the given number of ticks
    /// instead of persisting like a roster resident. Used by SituationRunner for traveler NPCs, which
    /// are never added to a settlement's roster.
    public void setDespawnTimer(int ticks) {
        this.despawnTicksRemaining = ticks;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.despawnTicksRemaining > 0 && --this.despawnTicksRemaining == 0) {
            this.discard();
        }
    }

    // ---- Lifecycle ----

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnData) {
        if (this.profileId().isEmpty()) {
            this.applyProfile(DEFAULT_PROFILE);
        }
        this.setPersistenceRequired();
        return super.finalizeSpawn(level, difficulty, spawnType, spawnData);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // Settlers are settlement residents managed by the roster, never despawned by distance.
        return false;
    }

    /// Death releases this settler's roster slot so the settlement can recruit a replacement (spawned
    /// by the next population pass once the settlement is calm — see SettlerPopulator). Without this,
    /// a dead resident's entry would be indistinguishable from one whose chunk is merely unloaded, and
    /// the population would shrink permanently.
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel serverLevel && this.settlementId != null) {
            SettlementManager manager = SettlementManager.get(serverLevel);
            manager.byId(this.settlementId).ifPresent(settlement -> settlement.roster().stream()
                    .filter(entry -> entry.entityId().filter(this.getUUID()::equals).isPresent())
                    .findFirst()
                    .ifPresent(entry -> {
                        settlement.recordChronicle(serverLevel.getGameTime(), "resident_died",
                                dev.campaigncore.settlers.settlement.SettlementLaborModel.professionOf(entry));
                        entry.unbind();
                        manager.markDirty();
                        SettlersMod.LOGGER.info("Settler {} of '{}' died; roster slot freed for a replacement.",
                                this.getUUID(), settlement.name());
                    }));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.profileId().ifPresent(id -> tag.putString("Profile", id.toString()));
        tag.putString("Texture", this.entityData.get(DATA_TEXTURE));
        tag.putString("JobOverlay", this.entityData.get(DATA_JOB_OVERLAY));
        if (this.settlementId != null) {
            tag.putUUID("SettlementId", this.settlementId);
        }
        writeBlockPos(tag, "HomeAnchor", this.homeAnchor);
        writeBlockPos(tag, "WorkAnchor", this.workAnchor);
        if (this.workAnchorType != null) {
            tag.putString("WorkAnchorType", this.workAnchorType.getSerializedName());
        }
        writeBlockPos(tag, "SleepAnchor", this.sleepAnchor);
        tag.putInt("DespawnTicksRemaining", this.despawnTicksRemaining);
    }

    private static void writeBlockPos(CompoundTag tag, String key, @Nullable BlockPos pos) {
        if (pos != null) {
            tag.putIntArray(key, new int[]{pos.getX(), pos.getY(), pos.getZ()});
        }
    }

    @Nullable
    private static BlockPos readBlockPos(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        int[] coords = tag.getIntArray(key);
        return coords.length == 3 ? new BlockPos(coords[0], coords[1], coords[2]) : null;
    }

    /// Guarded on tag.contains so saves predating the WorkAnchorType key read back as null (the
    /// populator retrofits the type on its next pass); an unrecognized name also fails soft to null.
    @Nullable
    private static AnchorType readAnchorType(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        return AnchorType.byName(tag.getString(key));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Texture")) {
            this.entityData.set(DATA_TEXTURE, tag.getString("Texture"));
        }
        if (tag.contains("JobOverlay")) {
            this.entityData.set(DATA_JOB_OVERLAY, tag.getString("JobOverlay"));
        }
        if (tag.contains("Profile")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("Profile"));
            if (id != null) {
                this.entityData.set(DATA_PROFILE, id.toString());
                // Re-resolve role from the datapack. Texture goes through assignBaseSkinIfNeeded so the
                // persisted random base skin (just restored above) is never clobbered by re-reading the
                // profile — only an explicit profile texture override may still override it.
                SettlerDataManager.profile(id).ifPresent(profile -> {
                    this.role = profile.role();
                    this.assignBaseSkinIfNeeded(profile.texture());
                    if (this.entityData.get(DATA_JOB_OVERLAY).isEmpty()) {
                        profile.jobOverlay().ifPresent(overlay -> this.entityData.set(DATA_JOB_OVERLAY, overlay.toString()));
                    }
                });
            }
        }
        if (tag.hasUUID("SettlementId")) {
            this.settlementId = tag.getUUID("SettlementId");
        }
        this.homeAnchor = readBlockPos(tag, "HomeAnchor");
        this.workAnchor = readBlockPos(tag, "WorkAnchor");
        this.workAnchorType = readAnchorType(tag, "WorkAnchorType");
        this.sleepAnchor = readBlockPos(tag, "SleepAnchor");
        if (tag.contains("DespawnTicksRemaining")) {
            this.despawnTicksRemaining = tag.getInt("DespawnTicksRemaining");
        }
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Locomotion (limb swing, head tracking) is procedural in SettlerModel; this controller carries
        // authored full-body/action clips. Idle is a near-empty base clip.
        controllers.add(new AnimationController<>(this, "base", 5, state -> {
            state.setAndContinue(IDLE);
            return PlayState.CONTINUE;
        }));
        // Theatrical work behavior: WorkBehavior triggers this periodically while a settler occupies a
        // work anchor. Animation + sound + position is sufficient for the beta; no simulated output.
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim(WORK_HAMMER_ANIM, RawAnimation.begin().thenPlay("animation.settler.work_hammer")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
