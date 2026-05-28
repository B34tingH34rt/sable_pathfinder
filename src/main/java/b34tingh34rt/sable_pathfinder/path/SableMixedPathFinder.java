package b34tingh34rt.sable_pathfinder.path;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

public final class SableMixedPathFinder {
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };
    private static final int MAX_VISITED = 4096;
    private static final double TRANSITION_COST = 0.35;

    private SableMixedPathFinder() {
    }

    public static Path findPath(final Level level, final Mob mob, final Set<BlockPos> targets, final float followRange, final int accuracy) {
        if (targets.isEmpty()) {
            return null;
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        final SablePathNode start = getStartNode(level, mob);
        final Set<SablePathNode> targetNodes = getTargetNodes(level, container, targets);
        if (targetNodes.isEmpty()) {
            return null;
        }

        final PriorityQueue<Entry> open = new PriorityQueue<>(Comparator.comparingDouble(entry -> entry.f));
        final Map<SablePathNode, Record> records = new HashMap<>();
        final Set<SablePathNode> closed = new HashSet<>();
        final Record startRecord = new Record(null, 0.0, heuristic(level, container, start, targetNodes));
        records.put(start, startRecord);
        open.add(new Entry(start, startRecord.f()));

        SablePathNode best = start;
        double bestDistance = startRecord.h();
        int visited = 0;

        while (!open.isEmpty() && visited++ < MAX_VISITED) {
            final SablePathNode current = open.poll().node();
            if (!closed.add(current)) {
                continue;
            }

            final double currentDistance = distanceToTargets(level, container, current, targetNodes);
            if (currentDistance < bestDistance) {
                best = current;
                bestDistance = currentDistance;
            }

            if (isTarget(level, container, current, targetNodes, accuracy)) {
                return buildPath(level, container, current, records, targets.iterator().next(), true);
            }

            for (final SablePathNode neighbor : neighbors(level, container, current)) {
                if (closed.contains(neighbor) || !isWalkable(level, neighbor)) {
                    continue;
                }

                final double stepCost = movementCost(level, container, current, neighbor);
                if (!Double.isFinite(stepCost)) {
                    continue;
                }

                final Record currentRecord = records.get(current);
                final double g = currentRecord.g() + stepCost;
                final Record existing = records.get(neighbor);
                if (existing != null && g >= existing.g()) {
                    continue;
                }

                final double h = heuristic(level, container, neighbor, targetNodes);
                final Record nextRecord = new Record(current, g, h);
                records.put(neighbor, nextRecord);
                open.add(new Entry(neighbor, nextRecord.f()));
            }
        }

        if (best != start && bestDistance <= followRange) {
            return buildPath(level, container, best, records, targets.iterator().next(), false);
        }

        return null;
    }

    public static boolean hasRelevantSubLevel(final Level level, final Mob mob, final Set<BlockPos> targets, final float followRange) {
        if (Sable.HELPER.getTrackingSubLevel(mob) != null || Sable.HELPER.getContaining(mob) != null) {
            return true;
        }

        for (final BlockPos target : targets) {
            if (Sable.HELPER.getContaining(level, target) != null || !intersectingAt(level, target.getCenter()).isEmpty()) {
                return true;
            }
        }

        final Vec3 mobPos = mob.position();
        final BoundingBox3d bounds = boundsAround(mobPos, targets, Math.min(64.0, Math.max(8.0, followRange)));
        return Sable.HELPER.getAllIntersecting(level, bounds).iterator().hasNext();
    }

    private static SablePathNode getStartNode(final Level level, final Mob mob) {
        final SubLevel tracking = Sable.HELPER.getTrackingSubLevel(mob);
        if (tracking != null) {
            return new SablePathNode(tracking.getUniqueId(), BlockPos.containing(tracking.logicalPose().transformPositionInverse(mob.position())));
        }

        final SubLevel containing = Sable.HELPER.getContaining(mob);
        if (containing != null) {
            return new SablePathNode(containing.getUniqueId(), mob.blockPosition());
        }

        return new SablePathNode(null, mob.blockPosition());
    }

    private static Set<SablePathNode> getTargetNodes(final Level level, final SubLevelContainer container, final Set<BlockPos> targets) {
        final Set<SablePathNode> nodes = new HashSet<>();
        for (final BlockPos target : targets) {
            nodes.add(new SablePathNode(null, target));

            final SubLevel direct = Sable.HELPER.getContaining(level, target);
            if (direct != null) {
                nodes.add(new SablePathNode(direct.getUniqueId(), target));
            }

            for (final SubLevel subLevel : intersectingAt(level, target.getCenter())) {
                nodes.add(encodeWorld(level, subLevel, target.getCenter()));
            }
        }
        return nodes;
    }

    private static List<SablePathNode> neighbors(final Level level, final SubLevelContainer container, final SablePathNode current) {
        final List<SablePathNode> result = new ArrayList<>(16);

        for (final int[] direction : DIRECTIONS) {
            for (int dy = -1; dy <= 1; dy++) {
                result.add(new SablePathNode(current.frame(), current.pos().offset(direction[0], dy, direction[1])));
            }
        }

        final Vec3 world = project(level, container, current);
        if (current.frame() != null) {
            result.add(new SablePathNode(null, BlockPos.containing(world)));
        }

        for (final SubLevel subLevel : intersectingAt(level, world)) {
            if (!Objects.equals(current.frame(), subLevel.getUniqueId())) {
                result.add(encodeWorld(level, subLevel, world));
            }
        }

        return result;
    }

    private static boolean isWalkable(final Level level, final SablePathNode node) {
        final BlockPos pos = node.pos();
        final BlockState feet = level.getBlockState(pos);
        final BlockState head = level.getBlockState(pos.above());
        final BlockState below = level.getBlockState(pos.below());
        return feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && !below.getCollisionShape(level, pos.below()).isEmpty();
    }

    private static double movementCost(final Level level, final SubLevelContainer container, final SablePathNode a, final SablePathNode b) {
        final Vec3 aw = project(level, container, a);
        final Vec3 bw = project(level, container, b);
        final double distance = aw.distanceTo(bw);
        if (distance > 4.0) {
            return Double.POSITIVE_INFINITY;
        }
        return distance + (Objects.equals(a.frame(), b.frame()) ? 0.0 : TRANSITION_COST);
    }

    private static boolean isTarget(final Level level, final SubLevelContainer container, final SablePathNode node, final Set<SablePathNode> targets, final int accuracy) {
        return distanceToTargets(level, container, node, targets) <= Math.max(accuracy, 1);
    }

    private static double heuristic(final Level level, final SubLevelContainer container, final SablePathNode node, final Set<SablePathNode> targets) {
        return distanceToTargets(level, container, node, targets);
    }

    private static double distanceToTargets(final Level level, final SubLevelContainer container, final SablePathNode node, final Set<SablePathNode> targets) {
        final Vec3 world = project(level, container, node);
        double best = Double.MAX_VALUE;
        for (final SablePathNode target : targets) {
            best = Math.min(best, world.distanceTo(project(level, container, target)));
        }
        return best;
    }

    private static Path buildPath(final Level level, final SubLevelContainer container, final SablePathNode end, final Map<SablePathNode, Record> records, final BlockPos target, final boolean reached) {
        final List<SablePathNode> reversed = new ArrayList<>();
        SablePathNode current = end;
        while (current != null) {
            reversed.add(current);
            current = records.get(current).previous();
        }

        final List<Node> vanillaNodes = new ArrayList<>(reversed.size());
        final List<UUID> frames = new ArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) {
            final SablePathNode node = reversed.get(index);
            final Node vanilla = new Node(node.pos().getX(), node.pos().getY(), node.pos().getZ());
            vanilla.type = PathType.WALKABLE;
            vanillaNodes.add(vanilla);
            frames.add(node.frame());
        }

        final Path path = new Path(vanillaNodes, target, reached);
        ((SablePathExtension) path).sablePathfinder$setFrames(level, frames);
        return path;
    }

    private static Vec3 project(final Level level, final SubLevelContainer container, final SablePathNode node) {
        if (node.frame() == null) {
            return Vec3.atCenterOf(node.pos());
        }

        final SubLevel subLevel = container.getSubLevel(node.frame());
        if (subLevel == null) {
            return Vec3.atCenterOf(node.pos());
        }

        return subLevel.logicalPose().transformPosition(Vec3.atCenterOf(node.pos()));
    }

    private static SablePathNode encodeWorld(final Level level, final SubLevel subLevel, final Vec3 world) {
        final Vec3 local = subLevel.logicalPose().transformPositionInverse(world);
        return new SablePathNode(subLevel.getUniqueId(), BlockPos.containing(local));
    }

    private static List<SubLevel> intersectingAt(final Level level, final Vec3 world) {
        final List<SubLevel> result = new ArrayList<>();
        for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(BlockPos.containing(world)))) {
            final Vec3 local = subLevel.logicalPose().transformPositionInverse(world);
            if (Objects.equals(Sable.HELPER.getContaining(level, local), subLevel)) {
                result.add(subLevel);
            }
        }
        return result;
    }

    private static BoundingBox3d boundsAround(final Vec3 mobPos, final Set<BlockPos> targets, final double padding) {
        double minX = mobPos.x;
        double minY = mobPos.y;
        double minZ = mobPos.z;
        double maxX = mobPos.x;
        double maxY = mobPos.y;
        double maxZ = mobPos.z;

        for (final BlockPos target : targets) {
            final Vec3 center = target.getCenter();
            minX = Math.min(minX, center.x);
            minY = Math.min(minY, center.y);
            minZ = Math.min(minZ, center.z);
            maxX = Math.max(maxX, center.x);
            maxY = Math.max(maxY, center.y);
            maxZ = Math.max(maxZ, center.z);
        }

        return new BoundingBox3d(minX - padding, minY - padding, minZ - padding, maxX + padding, maxY + padding, maxZ + padding);
    }

    private record Entry(SablePathNode node, double f) {
    }

    private record Record(SablePathNode previous, double g, double h) {
        double f() {
            return this.g + this.h;
        }
    }
}
