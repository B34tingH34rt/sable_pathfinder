package b34tingh34rt.sable_pathfinder.path;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SegmentedPathData {
    private final List<SegmentPoint> nodePoints;
    private final List<SegmentEdge> edges;

    public SegmentedPathData(final List<SegmentPoint> nodePoints, final List<SegmentEdge> edges) {
        this.nodePoints = List.copyOf(nodePoints);
        this.edges = List.copyOf(edges);
    }

    public boolean isEmpty() {
        return this.nodePoints.isEmpty() || this.edges.isEmpty();
    }

    public BlockPos getNodePos(final int index) {
        return this.pointForNode(index).projectedBlockPos();
    }

    public Vec3 getEntityPosAtNode(final Entity entity, final int index) {
        return this.pointForNode(index).entityPosition(entity);
    }

    public List<SegmentEdge> visibleEdges(final Path path, final int maxEdges) {
        if (this.edges.isEmpty() || maxEdges <= 0) {
            return List.of();
        }

        final int start = Math.max(0, Math.min(path.getNextNodeIndex(), this.edges.size() - 1));
        final int end = Math.min(this.edges.size(), start + maxEdges);
        return new ArrayList<>(this.edges.subList(start, end));
    }

    public SegmentedPathData truncate(final int length) {
        final int nodeCount = Math.max(0, Math.min(length, this.nodePoints.size()));
        final int edgeCount = Math.max(0, Math.min(nodeCount - 1, this.edges.size()));
        return new SegmentedPathData(this.nodePoints.subList(0, nodeCount), this.edges.subList(0, edgeCount));
    }

    public boolean shouldRecomputeForSubLevelBlockChange(final UUID subLevelId, final BlockPos changedLocalPos, final int nextNodeIndex) {
        if (this.edges.isEmpty()) {
            return false;
        }

        final int start = Math.max(0, Math.min(nextNodeIndex - 1, this.edges.size() - 1));
        for (int i = start; i < this.edges.size(); i++) {
            if (this.edgeTouchesChangedLocalBlock(this.edges.get(i), subLevelId, changedLocalPos)) {
                return true;
            }
        }

        return false;
    }

    public boolean shouldRecomputeForMovedSubLevels(final Level level, final int nextNodeIndex) {
        if (this.edges.isEmpty()) {
            return false;
        }

        final int start = Math.max(0, Math.min(nextNodeIndex - 1, this.edges.size() - 1));
        for (int i = start; i < this.edges.size(); i++) {
            if (this.edgeChangedBecauseSubLevelsMoved(level, this.edges.get(i))) {
                return true;
            }
        }

        return false;
    }

    private boolean edgeChangedBecauseSubLevelsMoved(final Level level, final SegmentEdge edge) {
        final SegmentPoint from = edge.from();
        final SegmentPoint to = edge.to();
        if (this.hasRemovedSubLevel(from) || this.hasRemovedSubLevel(to)) {
            return true;
        }

        if (!from.usesSubLevel() && !to.usesSubLevel()) {
            return this.worldEdgeNowIntersectsPathableSubLevel(level, from.worldPos().getCenter(), to.worldPos().getCenter());
        }

        if (from.usesSubLevel() != to.usesSubLevel()) {
            final SegmentPoint subLevelPoint = from.usesSubLevel() ? from : to;
            return subLevelPoint.projectedBlockPos().distManhattan(subLevelPoint.worldPos()) > 1;
        }

        if (!Objects.equals(from.subLevelId(), to.subLevelId())) {
            return from.projectedBlockPos().distManhattan(from.worldPos()) > 1 || to.projectedBlockPos().distManhattan(to.worldPos()) > 1;
        }

        return this.subLevelEdgeNoLongerMatchesExpectedSubLevel(level, from, to);
    }

    private boolean hasRemovedSubLevel(final SegmentPoint point) {
        return point.usesSubLevel() && point.subLevel().isRemoved();
    }

    private boolean worldEdgeNowIntersectsPathableSubLevel(final Level level, final Vec3 from, final Vec3 to) {
        final int steps = this.sampleSteps(from, to);
        for (int i = 0; i <= steps; i++) {
            final Vec3 sample = from.lerp(to, (double) i / (double) steps);
            if (this.findPathableSubLevelAt(level, sample) != null) {
                return true;
            }
        }

        return false;
    }

    private boolean subLevelEdgeNoLongerMatchesExpectedSubLevel(final Level level, final SegmentPoint from, final SegmentPoint to) {
        final UUID expectedSubLevelId = from.subLevelId();
        final Vec3 fromCenter = from.nodeCenter();
        final Vec3 toCenter = to.nodeCenter();
        final int steps = this.sampleSteps(fromCenter, toCenter);
        for (int i = 0; i <= steps; i++) {
            final Vec3 sample = fromCenter.lerp(toCenter, (double) i / (double) steps);
            final SubLevel actualSubLevel = this.findPathableSubLevelAt(level, sample);
            if (actualSubLevel == null || !Objects.equals(expectedSubLevelId, actualSubLevel.getUniqueId())) {
                return true;
            }
        }

        return false;
    }

    private int sampleSteps(final Vec3 from, final Vec3 to) {
        return Math.max(1, (int) Math.ceil(from.distanceTo(to) * 2.0D));
    }

    private SubLevel findPathableSubLevelAt(final Level level, final Vec3 worldPoint) {
        return Sable.HELPER.<SubLevel, SubLevel>runIncludingSubLevels(level, worldPoint, false, null, (candidateSubLevel, candidatePos) -> {
            if (candidateSubLevel == null || candidateSubLevel.getUniqueId() == null) {
                return null;
            }

            return this.isPathRelevantBlock(level, candidatePos) || this.isPathRelevantBlock(level, candidatePos.below()) ? candidateSubLevel : null;
        });
    }

    private boolean isPathRelevantBlock(final Level level, final BlockPos pos) {
        final BlockState blockState = level.getBlockState(pos);
        if (!blockState.isAir()) {
            return true;
        }

        final FluidState fluidState = level.getFluidState(pos);
        return !fluidState.isEmpty();
    }

    private boolean edgeTouchesChangedLocalBlock(final SegmentEdge edge, final UUID subLevelId, final BlockPos changedLocalPos) {
        final SegmentPoint from = edge.from();
        final SegmentPoint to = edge.to();
        final boolean fromMatches = from.usesSubLevel() && subLevelId.equals(from.subLevelId());
        final boolean toMatches = to.usesSubLevel() && subLevelId.equals(to.subLevelId());
        if (!fromMatches && !toMatches) {
            return false;
        }

        if (fromMatches && this.localPathPointTouches(from.localPos(), changedLocalPos)) {
            return true;
        }
        if (toMatches && this.localPathPointTouches(to.localPos(), changedLocalPos)) {
            return true;
        }

        if (fromMatches && toMatches) {
            return this.localSegmentTouches(from.localPos(), to.localPos(), changedLocalPos);
        }

        return false;
    }

    private boolean localPathPointTouches(final BlockPos pathLocalPos, final BlockPos changedLocalPos) {
        return changedLocalPos.distManhattan(pathLocalPos) <= 1 || changedLocalPos.distManhattan(pathLocalPos.below()) <= 1;
    }

    private boolean localSegmentTouches(final BlockPos from, final BlockPos to, final BlockPos changedLocalPos) {
        final int steps = Math.max(1, Math.max(Math.abs(to.getX() - from.getX()), Math.max(Math.abs(to.getY() - from.getY()), Math.abs(to.getZ() - from.getZ()))));
        for (int i = 0; i <= steps; i++) {
            final double progress = (double) i / (double) steps;
            final BlockPos sample = BlockPos.containing(
                    from.getX() + (to.getX() - from.getX()) * progress,
                    from.getY() + (to.getY() - from.getY()) * progress,
                    from.getZ() + (to.getZ() - from.getZ()) * progress
            );
            if (this.localPathPointTouches(sample, changedLocalPos)) {
                return true;
            }
        }

        return false;
    }

    private SegmentPoint pointForNode(final int index) {
        if (this.nodePoints.isEmpty()) {
            throw new IllegalStateException("Segmented path has no nodes.");
        }

        final int clamped = Math.max(0, Math.min(index, this.nodePoints.size() - 1));
        if (clamped > 0 && clamped - 1 < this.edges.size()) {
            return this.edges.get(clamped - 1).to();
        }

        if (clamped < this.edges.size()) {
            return this.edges.get(clamped).from();
        }

        return this.nodePoints.get(clamped);
    }
}
