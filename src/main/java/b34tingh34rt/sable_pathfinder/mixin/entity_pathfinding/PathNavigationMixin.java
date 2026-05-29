package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.entity.pathfinding.PathExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.Set;

/**
 * Central mixin for sub-level-aware pathfinding.
 *
 * Responsibilities:
 *
 * 1. createPath interception (unchanged from original for the mob-in/on-sublevel cases)
 *    When the mob or its target is known to be in a sub-level, run the original
 *    local-space pathfinding logic from the old addon.
 *
 * 2. createPath composite extension (new)
 *    After vanilla pathfinding runs (or after step 1), pass the resulting global-
 *    space path through CompositePathBuilder. If boundary crossings are detected,
 *    replace the path with a CompositePath that contains properly-reprojected and
 *    locally-refined segments for each sub-level.
 *
 * 3. tick interception (new)
 *    On every tick, check the active path:
 *    a. If it's a CompositePath and its current segment is done, advance to the
 *       next segment and reposition the mob into the new coordinate space.
 *    b. If it's a CompositePath and it's stale (sub-level moved), request a
 *       path recompute.
 */
@Mixin(value = PathNavigation.class, priority = 2000)
public abstract class PathNavigationMixin {

    @Shadow @Final protected Mob mob;
    @Shadow protected Path path;
    @Shadow @Final protected Level level;
    @Shadow private BlockPos targetPos;
    @Shadow private int reachRange;
    @Shadow @Final private PathFinder pathFinder;
    @Shadow private float maxVisitedNodesMultiplier;
    @Shadow private double speedModifier;

    @Shadow protected abstract boolean canUpdatePath();
    @Shadow protected abstract void resetStuckTimeout();
    @Shadow protected abstract Vec3 getTempMobPos();

    // -------------------------------------------------------------------------
    // 1 + 2: createPath interception
    // -------------------------------------------------------------------------

    @Inject(
            method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sablePathfinder$createPath(
            final Set<BlockPos> globalSet,
            final int i,
            final boolean bl,
            final int j,
            final float f,
            final CallbackInfoReturnable<Path> cir) {

        if (globalSet.isEmpty() || !this.canUpdatePath()) {
            return; // let vanilla handle the null/early-exit cases
        }

        // --- Determine if any sub-level is directly involved ---
        final Set<SubLevel> candidates = new ObjectOpenHashSet<>();
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this.mob);
        if (trackingSubLevel != null) candidates.add(trackingSubLevel);

        final SubLevel containingSubLevel = Sable.HELPER.getContaining(this.mob);
        if (containingSubLevel != null) candidates.add(containingSubLevel);

        for (final BlockPos globalPos : globalSet) {
            final SubLevel targetSubLevel = Sable.HELPER.getContaining(this.level, globalPos);
            if (targetSubLevel != null) candidates.add(targetSubLevel);
        }

        // --- Path already valid? ---
        if (this.path != null && !this.path.isDone() && globalSet.contains(this.targetPos)) {
            // If it's a composite path, only reuse if not stale
            if (this.path instanceof CompositePath cp && cp.isStale()) {
                // fall through to recompute
            } else {
                cir.setReturnValue(this.path);
                return;
            }
        }

        this.level.getProfiler().push("sable_pathfinder");

        Path result = null;

        if (!candidates.isEmpty()) {
            // --- Original local-space pathfinding for mob/target in a sub-level ---
            Path bestLocalPath = null;
            for (final SubLevel candidate : candidates) {
                final Path candidatePath = this.sablePathfinder$findLocalPath(candidate, globalSet, i, bl, j, f);
                if (candidatePath == null) continue;
                if (bestLocalPath == null
                        || candidatePath.canReach() && !bestLocalPath.canReach()
                        || candidatePath.canReach() == bestLocalPath.canReach()
                                && candidatePath.getNodeCount() < bestLocalPath.getNodeCount()
                        || candidatePath.canReach() == bestLocalPath.canReach()
                                && candidatePath.getNodeCount() == bestLocalPath.getNodeCount()
                                && candidatePath.getDistToTarget() < bestLocalPath.getDistToTarget()) {
                    bestLocalPath = candidatePath;
                }
            }
            result = bestLocalPath;
        }

        // If we didn't get a result from local-space logic, let vanilla run and
        // capture its output so we can inspect it for boundary crossings.
        // We do this by temporarily un-cancelling and re-running vanilla logic
        // inline using the same PathFinder that vanilla would use.
        if (result == null) {
            // Run vanilla equivalent: PathFinder.findPath in global space
            final BlockPos mobBlock = BlockPos.containing(
                    bl ? this.mob.position().add(0, 1, 0) : this.mob.position()
            );
            final int k = (int) (f + (float) i);
            final PathNavigationRegion region = new PathNavigationRegion(
                    this.level,
                    mobBlock.offset(-k, -k, -k),
                    mobBlock.offset(k, k, k)
            );
            result = this.pathFinder.findPath(region, this.mob, globalSet, f, j, this.maxVisitedNodesMultiplier);
        }

        // --- Attempt to build a CompositePath from whatever we have ---
        if (result != null) {
            final CompositePathBuilder builder = new CompositePathBuilder(
                    this.mob, this.level, this.pathFinder, this.maxVisitedNodesMultiplier
            );
            final CompositePath composite = builder.build(result, j, f);

            if (composite != null) {
                // Mark any sub-level segments' paths as local paths via Sable's
                // PathExtension interface so Sable knows they are sub-level-local.
                for (final PathSegment segment : composite.segments()) {
                    if (!segment.isGlobal()) {
                        ((PathExtension) segment.localPath).sable$setLocalPath(this.level, true);
                    }
                }

                // Set the composite's target and reach range
                if (composite.getTarget() != null) {
                    this.targetPos = composite.getTarget();
                    this.reachRange = j;
                    this.resetStuckTimeout();
                }

                this.level.getProfiler().pop();
                cir.setReturnValue(composite);
                return;
            }

            // No composite needed — use result as-is
            if (!candidates.isEmpty() && result.getTarget() != null) {
                // Apply sable local-path tag if the result came from local-space logic
                ((PathExtension) result).sable$setLocalPath(this.level, true);
                this.targetPos = result.getTarget();
                this.reachRange = j;
                this.resetStuckTimeout();
                this.level.getProfiler().pop();
                cir.setReturnValue(result);
                return;
            }

            // Pure vanilla result with no sub-level involvement — let it through
            // without cancelling so vanilla also sets targetPos etc. correctly.
            // We only cancel if we touched it ourselves.
            if (candidates.isEmpty()) {
                this.level.getProfiler().pop();
                // Don't cancel — let vanilla handle the path we computed inline
                // Actually, we need to cancel since we already ran vanilla's logic.
                this.targetPos = result.getTarget() != null ? result.getTarget() : this.targetPos;
                this.reachRange = j;
                this.resetStuckTimeout();
                cir.setReturnValue(result);
                return;
            }
        }

        this.level.getProfiler().pop();
    }

    // -------------------------------------------------------------------------
    // 3: tick interception — segment advancement and staleness check
    // -------------------------------------------------------------------------

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void sablePathfinder$tick(final CallbackInfo ci) {
        if (!(this.path instanceof final CompositePath composite)) {
            return; // not a composite path, nothing to do
        }

        // Staleness check — if the sub-level has moved, invalidate the path
        if (composite.isStale()) {
            // Clear the path; vanilla navigation will request a new one on next
            // goal evaluation. This is the safest approach — don't try to
            // recompute here since we're inside tick().
            this.path = null;
            return;
        }

        final PathSegment current = composite.currentSegment();

        // Check if the current segment is done
        if (!current.localPath.isDone()) {
            return;
        }

        // Current segment finished — try to advance
        if (composite.isLastSegment()) {
            // Entire composite path is done; let vanilla clean up naturally
            return;
        }

        // Advance to the next segment
        composite.advanceSegment();
        final PathSegment next = composite.currentSegment();

        // Reposition the mob into the next coordinate space
        this.sablePathfinder$transitionToSegment(current, next);
    }

    // -------------------------------------------------------------------------
    // Segment transition — coordinate space change
    // -------------------------------------------------------------------------

    /**
     * Handle the coordinate space transition when moving from one segment to the
     * next.
     *
     * Cases:
     *   global  → sub-level : transform mob position into sub-level local space
     *   sub-level → global  : transform mob position out to global space
     *   sub-level → sub-level : transform out of old, into new
     *   global  → global   : no transform needed (shouldn't happen — composite
     *                         wouldn't have been built)
     */
    private void sablePathfinder$transitionToSegment(
            final PathSegment from,
            final PathSegment to) {

        Vec3 mobPos = this.mob.position();

        // Transform out of the 'from' space into global space
        if (from.subLevel != null) {
            final Pose3d fromPose = from.subLevel.logicalPose();
            mobPos = fromPose.transformPosition(mobPos);
        }

        // Transform into the 'to' space from global space
        if (to.subLevel != null) {
            final Pose3d toPose = to.subLevel.logicalPose();
            mobPos = toPose.transformPositionInverse(mobPos);
        }

        this.mob.setPos(mobPos.x, mobPos.y, mobPos.z);
        this.resetStuckTimeout();
    }

    // -------------------------------------------------------------------------
    // Original local-space pathfinding helper (preserved from original addon)
    // -------------------------------------------------------------------------

    private Path sablePathfinder$findLocalPath(
            final SubLevel candidateSubLevel,
            final Set<BlockPos> globalSet,
            final int i,
            final boolean bl,
            final int j,
            final float f) {

        final Pose3d pose = candidateSubLevel.logicalPose();
        final Vec3 localMobPosition = pose.transformPositionInverse(this.mob.position());
        final BlockPos localMobBlockPosition = BlockPos.containing(localMobPosition);
        final Set<BlockPos> localSet = new ObjectOpenHashSet<>();

        for (final BlockPos globalPos : globalSet) {
            if (Objects.equals(Sable.HELPER.getContaining(this.level, globalPos), candidateSubLevel)) {
                localSet.add(globalPos);
                continue;
            }
            final Vec3 localPosVec = pose.transformPositionInverse(globalPos.getCenter());
            localSet.add(BlockPos.containing(localPosVec));
        }

        final BlockPos blockPos = bl ? localMobBlockPosition.above() : localMobBlockPosition;
        final int k = (int) (f + (float) i);
        final PathNavigationRegion pathNavigationRegion = new PathNavigationRegion(
                this.level,
                blockPos.offset(-k, -k, -k),
                blockPos.offset(k, k, k)
        );
        return this.pathFinder.findPath(
                pathNavigationRegion, this.mob, localSet, f, j, this.maxVisitedNodesMultiplier
        );
    }
}
