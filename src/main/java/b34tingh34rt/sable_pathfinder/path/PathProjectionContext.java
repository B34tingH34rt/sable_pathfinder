package b34tingh34rt.sable_pathfinder.path;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PathProjectionContext {
    private static final double MAX_PROJECTED_CAPTURE_DISTANCE_SQR = 16.0D * 16.0D;

    private static final ThreadLocal<ArrayDeque<State>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);

    private PathProjectionContext() {
    }

    public static void begin(final Level level, final Mob mob) {
        ACTIVE.get().push(new State(level, mob));
    }

    public static void end() {
        final ArrayDeque<State> stack = ACTIVE.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            ACTIVE.remove();
        }
    }

    public static void recordResolvedBlock(final BlockPos worldQueryPos, @Nullable final SubLevel subLevel, final BlockPos localPos) {
        final State state = current();
        if (state == null || subLevel == null || subLevel.getUniqueId() == null) {
            return;
        }

        state.recordResolvedBlock(worldQueryPos, subLevel, localPos);
    }

    public static void attachToPath(final Path path) {
        final State state = current();
        if (state == null || path == null || path.getNodeCount() < 2 || state.resolvedBlocks.isEmpty()) {
            return;
        }

        if (state.pathUsesSubLevelStorageCoordinates(path)) {
            return;
        }

        final List<SegmentPoint> points = new ArrayList<>(path.getNodeCount());
        for (int i = 0; i < path.getNodeCount(); i++) {
            points.add(SegmentPoint.world(path.getNode(i).asBlockPos()));
        }

        boolean usesSubLevel = false;
        final List<SegmentEdge> edges = new ArrayList<>(path.getNodeCount() - 1);
        for (int i = 1; i < path.getNodeCount(); i++) {
            final SegmentEdge edge = state.resolveEdge(path.getNode(i - 1), path.getNode(i));
            edges.add(edge);
            usesSubLevel |= edge.from().usesSubLevel() || edge.to().usesSubLevel();
        }

        if (usesSubLevel) {
            ((SegmentedPathAccess) path).sablePathfinder$setSegmentedPathData(new SegmentedPathData(points, edges));
        }
    }

    @Nullable
    private static State current() {
        final ArrayDeque<State> stack = ACTIVE.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    private static final class State {
        private final Level level;
        private final Mob mob;
        private final Map<BlockPos, SegmentPoint> resolvedBlocks = new HashMap<>();

        private State(final Level level, final Mob mob) {
            this.level = level;
            this.mob = mob;
        }

        private SegmentEdge resolveEdge(final Node fromNode, final Node toNode) {
            final BlockPos fromWorldPos = fromNode.asBlockPos();
            final BlockPos toWorldPos = toNode.asBlockPos();
            return new SegmentEdge(this.resolveNodeSurface(fromWorldPos), this.resolveNodeSurface(toWorldPos));
        }

        private void recordResolvedBlock(final BlockPos worldQueryPos, final SubLevel subLevel, final BlockPos localPos) {
            if (this.isSubLevelStoragePosition(worldQueryPos)) {
                return;
            }

            final SegmentPoint point = SegmentPoint.subLevel(worldQueryPos.immutable(), subLevel, localPos.immutable());
            if (!this.projectsNearWorldQuery(point, worldQueryPos)) {
                return;
            }

            this.resolvedBlocks.putIfAbsent(worldQueryPos.immutable(), point);
        }

        private boolean pathUsesSubLevelStorageCoordinates(final Path path) {
            for (int i = 0; i < path.getNodeCount(); i++) {
                if (this.isSubLevelStoragePosition(path.getNode(i).asBlockPos())) {
                    return true;
                }
            }

            return false;
        }

        private boolean isSubLevelStoragePosition(final BlockPos pos) {
            return Sable.HELPER.getContaining(this.level, pos) != null;
        }

        private boolean projectsNearWorldQuery(final SegmentPoint point, final BlockPos worldQueryPos) {
            final Vec3 projectedCenter = point.nodeCenter();
            return projectedCenter.distanceToSqr(worldQueryPos.getCenter()) <= MAX_PROJECTED_CAPTURE_DISTANCE_SQR;
        }

        private SegmentPoint resolveNodeSurface(final BlockPos nodeWorldPos) {
            final SegmentPoint exact = this.resolvedBlocks.get(nodeWorldPos);
            if (exact != null && exact.usesSubLevel()) {
                return exact;
            }

            final SegmentPoint support = this.resolvedBlocks.get(nodeWorldPos.below());
            if (support != null && support.usesSubLevel()) {
                return SegmentPoint.subLevel(nodeWorldPos, support.subLevel(), support.localPos().above());
            }

            return SegmentPoint.world(nodeWorldPos);
        }

        private SegmentPoint projectWorldPointThroughSubLevel(final BlockPos worldPos, final SubLevel subLevel) {
            final BlockPos localPos = BlockPos.containing(subLevel.logicalPose().transformPositionInverse(worldPos.getCenter()));
            return SegmentPoint.subLevel(worldPos, subLevel, localPos);
        }
    }
}
