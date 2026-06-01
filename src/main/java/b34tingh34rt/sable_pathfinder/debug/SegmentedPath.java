package b34tingh34rt.sable_pathfinder.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class SegmentedPath extends Path {
    private final List<Node> nodes;

    private SegmentedPath(final List<Node> nodes, final BlockPos target, final boolean reached) {
        super(nodes, target, reached);
        this.nodes = nodes;
    }

    public static Path wrap(final Path path) {
        if (path == null || path instanceof SegmentedPath) {
            return path;
        }

        final List<Node> nodes = new ArrayList<>(path.getNodeCount());
        for (int i = 0; i < path.getNodeCount(); i++) {
            nodes.add(path.getNode(i));
        }

        final SegmentedPath segmentedPath = new SegmentedPath(nodes, path.getTarget(), path.canReach());
        segmentedPath.setNextNodeIndex(path.getNextNodeIndex());
        return segmentedPath;
    }

    @Override
    public Vec3 getEntityPosAtNode(final Entity entity, final int index) {
        final Vec3 projected = PathNodeDebugState.entityPosFor(this.nodes.get(index), entity);
        return projected == null ? super.getEntityPosAtNode(entity, index) : projected;
    }

    @Override
    public BlockPos getNodePos(final int index) {
        final BlockPos projected = PathNodeDebugState.blockPosFor(this.nodes.get(index));
        return projected == null ? super.getNodePos(index) : projected;
    }

    @Override
    public Vec3 getNextEntityPos(final Entity entity) {
        return this.getEntityPosAtNode(entity, this.getNextNodeIndex());
    }

    @Override
    public BlockPos getNextNodePos() {
        return this.getNodePos(this.getNextNodeIndex());
    }

    @Override
    public Path copy() {
        final SegmentedPath copy = new SegmentedPath(new ArrayList<>(this.nodes), this.getTarget(), this.canReach());
        copy.setNextNodeIndex(this.getNextNodeIndex());
        return copy;
    }
}
