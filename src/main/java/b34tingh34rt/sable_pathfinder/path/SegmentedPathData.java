package b34tingh34rt.sable_pathfinder.path;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

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
