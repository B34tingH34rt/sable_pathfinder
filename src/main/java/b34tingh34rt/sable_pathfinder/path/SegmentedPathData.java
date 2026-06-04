package b34tingh34rt.sable_pathfinder.path;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
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
