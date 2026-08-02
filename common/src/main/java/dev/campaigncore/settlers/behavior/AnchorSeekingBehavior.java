package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/// Base for "walk to an anchor, then do something there" behaviors: MoveToAnchor plus the arrival
/// hook the design calls for. After a long failed journey, an unobserved settler who has not recently
/// been in combat may leash to a safe position beside the destination. Otherwise the timeout drops
/// control back to the schedule for re-evaluation.
public abstract class AnchorSeekingBehavior implements SettlerBehavior {
    private static final double ARRIVE_DIST_SQ = 4.0;
    private static final double ACTIVITY_RADIUS_SQ = 8.0 * 8.0;
    /// Thirty seconds of ordinary pathfinding before an off-screen leash is considered.
    private static final int TIMEOUT_TICKS = 600;
    /// Combat remains "recent" for ten seconds after dealing or receiving damage.
    private static final int RECENT_COMBAT_TICKS = 200;
    /// Conservative visibility radius: even a distant player with line of sight suppresses leashing.
    private static final double OBSERVER_RADIUS_SQ = 128.0 * 128.0;

    private final AnchorType anchorType;
    @Nullable
    private BlockPos target;
    private int giveUpTicks;
    private boolean reachedAnchor;
    private int wanderCooldown;

    protected AnchorSeekingBehavior(AnchorType anchorType) {
        this.anchorType = anchorType;
    }

    @Override
    public void start(SettlerEntity settler, Settlement settlement) {
        retarget(settler, settlement);
    }

    /// Re-picks the target and resets the timeout without any other start-up work. Subclasses with
    /// multi-stop routes (e.g. PatrolBehavior) call this on arrival instead of {@link #start}, so
    /// per-arrival bookkeeping (like advancing a route index) isn't clobbered by full re-initialization.
    protected void retarget(SettlerEntity settler, Settlement settlement) {
        this.target = pickTarget(settler, settlement).orElse(null);
        this.giveUpTicks = TIMEOUT_TICKS;
        this.reachedAnchor = false;
        this.wanderCooldown = 0;
    }

    /// Where to walk. Default: a random anchor of this behavior's type; subclasses may prefer a
    /// settler-specific binding (home/work anchor) first.
    protected Optional<BlockPos> pickTarget(SettlerEntity settler, Settlement settlement) {
        return settlement.anchors().random(this.anchorType, settler.getRandom());
    }

    protected double speed() {
        return 1.0;
    }

    @Override
    public boolean tick(SettlerEntity settler, Settlement settlement, ServerLevel level) {
        if (this.target == null) {
            return false;
        }
        if (this.reachedAnchor) {
            return tickAroundAnchor(settler, settlement, level);
        }
        if (settler.blockPosition().distSqr(this.target) > ARRIVE_DIST_SQ) {
            if (settler.getNavigation().isDone()) {
                settler.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, speed());
            }
            if (--this.giveUpTicks > 0) {
                return true;
            }
            if (canLeash(settler, level) && leashToTarget(settler)) {
                return reachAnchor(settler, settlement, level);
            }
            return false;
        }
        return reachAnchor(settler, settlement, level);
    }

    /// Called every tick once within arrival distance of the target.
    protected void onArrive(SettlerEntity settler, Settlement settlement, ServerLevel level) {
    }

    protected Optional<BlockPos> target() {
        return Optional.ofNullable(this.target);
    }

    private boolean reachAnchor(SettlerEntity settler, Settlement settlement, ServerLevel level) {
        BlockPos reached = this.target;
        // The original path still points at the exact block. Cancel it as soon as the activity area is
        // reached so the settler does not keep pressing into a bed, barrel, door, or workstation.
        settler.getNavigation().stop();
        onArrive(settler, settlement, level);
        // Route behaviors retarget from onArrive; let them immediately start toward the next anchor.
        if (!java.util.Objects.equals(reached, this.target)) {
            return true;
        }
        this.reachedAnchor = true;
        this.wanderCooldown = nextWanderDelay(settler);
        return true;
    }

    private boolean tickAroundAnchor(SettlerEntity settler, Settlement settlement, ServerLevel level) {
        if (this.target == null) {
            return false;
        }
        onArrive(settler, settlement, level);

        double distance = settler.blockPosition().distSqr(this.target);
        if (distance > ACTIVITY_RADIUS_SQ) {
            if (settler.getNavigation().isDone()) {
                settler.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(),
                        this.target.getZ() + 0.5, speed());
            }
            return true;
        }

        if (--this.wanderCooldown <= 0 && settler.getNavigation().isDone()) {
            this.wanderCooldown = nextWanderDelay(settler);
            for (int attempt = 0; attempt < 6; attempt++) {
                Vec3 destination = DefaultRandomPos.getPos(settler, 8, 3);
                if (destination != null && destination.distanceToSqr(Vec3.atCenterOf(this.target))
                        <= ACTIVITY_RADIUS_SQ) {
                    settler.getNavigation().moveTo(destination.x, destination.y, destination.z, speed() * 0.75);
                    break;
                }
            }
        }
        return true;
    }

    private static int nextWanderDelay(SettlerEntity settler) {
        return 40 + settler.getRandom().nextInt(81);
    }

    private static boolean canLeash(SettlerEntity settler, ServerLevel level) {
        if (settler.getTarget() != null) {
            return false;
        }
        int tick = settler.tickCount;
        if (tick - settler.getLastHurtByMobTimestamp() < RECENT_COMBAT_TICKS
                || tick - settler.getLastHurtMobTimestamp() < RECENT_COMBAT_TICKS) {
            return false;
        }
        return level.players().stream().noneMatch(player -> !player.isSpectator() && player.isAlive()
                && player.distanceToSqr(settler) <= OBSERVER_RADIUS_SQ
                && player.hasLineOfSight(settler));
    }

    private boolean leashToTarget(SettlerEntity settler) {
        if (this.target == null) {
            return false;
        }
        // randomTeleport validates collision and footing. Try the anchor, the space above it, then
        // its four horizontal neighbors because workstation anchors are often solid blocks.
        if (tryLeash(settler, this.target) || tryLeash(settler, this.target.above())) {
            return true;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (tryLeash(settler, this.target.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryLeash(SettlerEntity settler, BlockPos destination) {
        return settler.randomTeleport(destination.getX() + 0.5, destination.getY(),
                destination.getZ() + 0.5, false);
    }
}
