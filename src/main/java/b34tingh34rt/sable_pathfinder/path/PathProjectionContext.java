package b34tingh34rt.sable_pathfinder.path;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
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
import java.util.Optional;

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

        state.resolvedBlocks.put(worldQueryPos.immutable(), SegmentPoint.subLevel(worldQueryPos.immutable(), subLevel, localPos.immutable()));
    }

    public static void attachToPath(final Path path) {
        final State state = current();
        if (state == null || path == null || path.getNodeCount() < 2 || state.resolvedBlocks.isEmpty()) {
            return;
        }

        final List<SegmentPoint> points = new ArrayList<>(path.getNodeCount());
        boolean usesSubLevel = false;
        for (int i = 0; i < path.getNodeCount(); i++) {
            final SegmentPoint point = state.resolveNode(path.getNode(i));
            points.add(point);
            usesSubLevel |= point.usesSubLevel();
        }

        final List<SegmentEdge> edges = new ArrayList<>(path.getNodeCount() - 1);
        for (int i = 1; i < path.getNodeCount(); i++) {
            final SegmentEdge edge = state.resolveEdge(points.get(i - 1), points.get(i));
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

        private SegmentPoint resolveNode(final Node node) {
            final BlockPos nodePos = node.asBlockPos();
            final SegmentPoint exact = this.resolvedBlocks.get(nodePos);
            if (exact != null) {
                return exact;
            }

            final SegmentPoint support = this.resolvedBlocks.get(nodePos.below());
            if (support != null && support.usesSubLevel()) {
                return SegmentPoint.subLevel(nodePos, support.subLevel(), support.localPos().above());
            }

            return SegmentPoint.world(nodePos);
        }

        private SegmentEdge resolveEdge(final SegmentPoint from, final SegmentPoint to) {
            if (from.usesSubLevel() || to.usesSubLevel()) {
                return new SegmentEdge(from, to);
            }

            final Optional<SubLevel> crossingSubLevel = this.findCrossingSubLevel(from.worldPos().getCenter(), to.worldPos().getCenter());
            if (crossingSubLevel.isEmpty()) {
                return new SegmentEdge(from, to);
            }

            final SubLevel subLevel = crossingSubLevel.get();
            return new SegmentEdge(this.projectWorldPointThroughSubLevel(from.worldPos(), subLevel), this.projectWorldPointThroughSubLevel(to.worldPos(), subLevel));
        }

        private Optional<SubLevel> findCrossingSubLevel(final Vec3 from, final Vec3 to) {
            for (int i = 1; i <= 3; i++) {
                final Vec3 sample = from.lerp(to, i * 0.25D);
                final SubLevel subLevel = Sable.HELPER.<SubLevel, SubLevel>runIncludingSubLevels(this.level, sample, false, null, (candidateSubLevel, candidatePos) -> candidateSubLevel);
                if (subLevel != null) {
                    return Optional.of(subLevel);
                }
            }

            return Optional.empty();
        }

        private SegmentPoint projectWorldPointThroughSubLevel(final BlockPos worldPos, final SubLevel subLevel) {
            final BlockPos localPos = BlockPos.containing(subLevel.logicalPose().transformPositionInverse(worldPos.getCenter()));
            return SegmentPoint.subLevel(worldPos, subLevel, localPos);
        }
    }
}
