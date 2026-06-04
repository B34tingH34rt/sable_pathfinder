package b34tingh34rt.sable_pathfinder.path;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PathProjectionContext {
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

        state.resolvedBlocks.putIfAbsent(worldQueryPos.immutable(), SegmentPoint.subLevel(worldQueryPos.immutable(), subLevel, localPos.immutable()));
    }

    public static void attachToPath(final Path path) {
        final State state = current();
        if (state == null || path == null || path.getNodeCount() < 2 || state.resolvedBlocks.isEmpty()) {
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
