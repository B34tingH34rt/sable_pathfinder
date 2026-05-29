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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.Set;

@Mixin(value = PathNavigation.class, priority = 2000)
public abstract class PathNavigationMixin {
    @Shadow
    @Final
    protected Mob mob;

    @Shadow
    protected Path path;

    @Shadow
    @Final
    protected Level level;

    @Shadow
    private BlockPos targetPos;

    @Shadow
    private int reachRange;

    @Shadow
    @Final
    private PathFinder pathFinder;

    @Shadow
    private float maxVisitedNodesMultiplier;

    @Shadow
    protected abstract boolean canUpdatePath();

    @Shadow
    protected abstract void resetStuckTimeout();

    @Shadow
    protected abstract void doStuckDetection(Vec3 positionVec3);

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void sablePathfinder$createPath(final Set<BlockPos> globalSet, final int i, final boolean bl, final int j, final float f, final CallbackInfoReturnable<Path> cir) {
        final Set<SubLevel> candidates = new ObjectOpenHashSet<>();
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this.mob);
        if (trackingSubLevel != null) {
            candidates.add(trackingSubLevel);
        }

        final SubLevel containingSubLevel = Sable.HELPER.getContaining(this.mob);
        if (containingSubLevel != null) {
            candidates.add(containingSubLevel);
        }

        for (final BlockPos globalPos : globalSet) {
            final SubLevel targetSubLevel = Sable.HELPER.getContaining(this.level, globalPos);
            if (targetSubLevel != null) {
                candidates.add(targetSubLevel);
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        if (globalSet.isEmpty()) {
            cir.setReturnValue(null);
            return;
        }

        if (!this.canUpdatePath()) {
            cir.setReturnValue(null);
            return;
        }

        if (this.path != null && !this.path.isDone() && globalSet.contains(this.targetPos)) {
            cir.setReturnValue(this.path);
            return;
        }

        this.level.getProfiler().push("pathfind_sub_level");

        Path bestPath = null;
        for (final SubLevel candidate : candidates) {
            final Path candidatePath = this.sablePathfinder$findLocalPath(candidate, globalSet, i, bl, j, f);
            if (candidatePath == null) {
                continue;
            }

            if (bestPath == null
                    || candidatePath.canReach() && !bestPath.canReach()
                    || candidatePath.canReach() == bestPath.canReach() && candidatePath.getNodeCount() < bestPath.getNodeCount()
                    || candidatePath.canReach() == bestPath.canReach() && candidatePath.getNodeCount() == bestPath.getNodeCount() && candidatePath.getDistToTarget() < bestPath.getDistToTarget()) {
                bestPath = candidatePath;
            }
        }

        this.level.getProfiler().pop();

        if (bestPath != null && bestPath.getTarget() != null) {
            this.targetPos = bestPath.getTarget();
            this.reachRange = j;
            this.resetStuckTimeout();
            ((PathExtension) bestPath).sable$setLocalPath(this.level, true);
        }

        cir.setReturnValue(bestPath);
    }

    private Path sablePathfinder$findLocalPath(final SubLevel candidateSubLevel, final Set<BlockPos> globalSet, final int i, final boolean bl, final int j, final float f) {
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
        final PathNavigationRegion pathNavigationRegion = new PathNavigationRegion(this.level, blockPos.offset(-k, -k, -k), blockPos.offset(k, k, k));
        return this.pathFinder.findPath(pathNavigationRegion, this.mob, localSet, f, j, this.maxVisitedNodesMultiplier);
    }
}