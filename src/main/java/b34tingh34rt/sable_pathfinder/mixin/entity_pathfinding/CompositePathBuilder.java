package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a CompositePath from a vanilla global-space Path.
 *
 * Pipeline:
 *   1. Walk the global path node-by-node, calling getContaining() on each node
 *      position to detect when nodes cross sub-level boundaries.
 *   2. Split the node list into raw segments at each boundary crossing.
 *   3. For each sub-level segment, reproject its nodes from global space into
 *      the sub-level's local coordinate space using Pose3d.transformPositionInverse().
 *   4. Run a second pathfinder pass (local refinement) for each sub-level segment,
 *      using the entry and exit transition positions in local space. This produces
 *      a clean locally-optimal path rather than the reprojected approximation.
 *   5. Wrap everything into a CompositePath.
 *
 * If the global path has no sub-level boundary crossings, returns null to signal
 * that the vanilla path can be used as-is (no composite overhead needed).
 */
public final class CompositePathBuilder {

    private final Mob mob;
    private final Level level;
    private final PathFinder pathFinder;
    private final float maxVisitedNodesMultiplier;

    public CompositePathBuilder(
            final Mob mob,
            final Level level,
            final PathFinder pathFinder,
            final float maxVisitedNodesMultiplier) {
        this.mob = mob;
        this.level = level;
        this.pathFinder = pathFinder;
        this.maxVisitedNodesMultiplier = maxVisitedNodesMultiplier;
    }

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Attempt to build a CompositePath from a vanilla global-space path.
     *
     * Returns null if:
     *   - the global path has no sub-level boundary crossings (use vanilla path)
     *   - the global path is null or has fewer than 2 nodes
     *
     * The returned CompositePath may have fewer segments than crossings if some
     * local refinement passes fail; degenerate segments fall back to their
     * reprojected approximation.
     *
     * @param globalPath   the vanilla path computed in global space
     * @param accuracy     reach distance passed to the PathFinder for refinement
     * @param f            max range (used to size PathNavigationRegion)
     */
    @Nullable
    public CompositePath build(
            final Path globalPath,
            final int accuracy,
            final float f) {

        if (globalPath == null || globalPath.getNodeCount() < 2) {
            return null;
        }

        // Step 1 + 2: walk nodes and split into raw segments
        final List<RawSegment> rawSegments = this.detectAndSlice(globalPath);

        if (rawSegments.size() <= 1) {
            // No boundary crossings — single space, no composite needed
            return null;
        }

        // Step 3 + 4: reproject and refine each segment
        final List<PathSegment> segments = new ArrayList<>(rawSegments.size());

        for (int i = 0; i < rawSegments.size(); i++) {
            final RawSegment raw = rawSegments.get(i);
            final PathSegment segment = this.buildSegment(raw, accuracy, f);
            if (segment == null) {
                // Refinement failed entirely for this segment — abort composite,
                // let vanilla path handle it
                return null;
            }
            segments.add(segment);
        }

        return new CompositePath(segments);
    }

    // -------------------------------------------------------------------------
    // Step 1 + 2: detect boundary crossings and slice into raw segments
    // -------------------------------------------------------------------------

    private List<RawSegment> detectAndSlice(final Path globalPath) {
        final List<RawSegment> result = new ArrayList<>();

        SubLevel currentSpace = null; // null = global world
        List<Node> currentNodes = new ArrayList<>();
        Vec3 currentEntryGlobal = null; // null for first segment

        for (int i = 0; i < globalPath.getNodeCount(); i++) {
            final Node node = globalPath.getNode(i);
            final BlockPos globalPos = node.asBlockPos();
            final SubLevel nodeSpace = Sable.HELPER.getContaining(this.level, globalPos);

            if (!Objects.equals(nodeSpace, currentSpace)) {
                // Boundary crossing detected
                if (!currentNodes.isEmpty()) {
                    // Close the current raw segment
                    // The exit point is the center of the last node in this segment
                    final Vec3 exitGlobal = Vec3.atCenterOf(currentNodes.get(currentNodes.size() - 1).asBlockPos());
                    result.add(new RawSegment(currentSpace, new ArrayList<>(currentNodes), currentEntryGlobal, exitGlobal));
                }

                // Start a new segment
                currentSpace = nodeSpace;
                currentNodes = new ArrayList<>();
                // The entry point is the center of the first node in the new segment
                currentEntryGlobal = Vec3.atCenterOf(globalPos);
            }

            currentNodes.add(node);
        }

        // Close the final segment (no exit point — it's the last one)
        if (!currentNodes.isEmpty()) {
            result.add(new RawSegment(currentSpace, currentNodes, currentEntryGlobal, null));
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Step 3 + 4: reproject and refine a single raw segment into a PathSegment
    // -------------------------------------------------------------------------

    @Nullable
    private PathSegment buildSegment(
            final RawSegment raw,
            final int accuracy,
            final float f) {

        if (raw.subLevel == null) {
            // Global segment — nodes are already in global space.
            // Reconstruct a Path from the raw nodes.
            final Path globalSegmentPath = pathFromNodes(raw.nodes, raw.nodes.get(raw.nodes.size() - 1));
            if (globalSegmentPath == null) {
                return null;
            }
            return new PathSegment(globalSegmentPath, null, raw.entryGlobal, raw.exitGlobal);
        }

        // Sub-level segment — reproject nodes into local space, then refine.
        final Pose3d pose = raw.subLevel.logicalPose();

        // --- Step 3: Reproject ---
        // Transform every node from global space into the sub-level's local space.
        final List<Node> localNodes = reprojectNodes(raw.nodes, pose);

        // The reprojected path — used as fallback if refinement fails.
        final Path reprojectedPath = pathFromNodes(localNodes, localNodes.get(localNodes.size() - 1));

        // --- Step 4: Local refinement ---
        // Determine entry and exit positions in local space.
        // Entry: the first node in this segment (already reprojected).
        // Exit (target): the last node in this segment (already reprojected).
        final BlockPos localEntry = localNodes.get(0).asBlockPos();
        final BlockPos localExit = localNodes.get(localNodes.size() - 1).asBlockPos();

        // Size the PathNavigationRegion to cover the bounding box of the local
        // nodes with some padding.
        final int k = (int) (f + (float) accuracy);
        final PathNavigationRegion region = new PathNavigationRegion(
                this.level,
                localEntry.offset(-k, -k, -k),
                localExit.offset(k, k, k)
        );

        // Run the pathfinder from the mob's local-space position to the local
        // exit target. We use the first node position as the mob's local
        // origin since the mob may not physically be there yet.
        final Path refinedPath = this.pathFinder.findPath(
                region,
                this.mob,
                Set.of(localExit),
                f,
                accuracy,
                this.maxVisitedNodesMultiplier
        );

        // Use refined path if it's valid and reachable; otherwise fall back to
        // the reprojected approximation.
        final Path finalPath;
        if (refinedPath != null && refinedPath.canReach()) {
            finalPath = refinedPath;
        } else if (reprojectedPath != null) {
            finalPath = reprojectedPath;
        } else {
            return null;
        }

        return new PathSegment(finalPath, raw.subLevel, raw.entryGlobal, raw.exitGlobal);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Reproject a list of nodes from global space into a sub-level's local
     * coordinate space using the sub-level's logical pose inverse transform.
     */
    private static List<Node> reprojectNodes(final List<Node> globalNodes, final Pose3d pose) {
        final List<Node> local = new ArrayList<>(globalNodes.size());
        for (final Node globalNode : globalNodes) {
            final Vec3 globalCenter = Vec3.atCenterOf(globalNode.asBlockPos());
            final Vec3 localVec = pose.transformPositionInverse(globalCenter);
            final BlockPos localPos = BlockPos.containing(localVec);
            // Copy the node with updated position, preserving type and cost fields.
            final Node localNode = new Node(localPos.getX(), localPos.getY(), localPos.getZ());
            localNode.type = globalNode.type;
            localNode.costMalus = globalNode.costMalus;
            localNode.closed = globalNode.closed;
            localNode.walkedDistance = globalNode.walkedDistance;
            localNode.g = globalNode.g;
            localNode.h = globalNode.h;
            local.add(localNode);
        }
        return local;
    }

    /**
     * Construct a vanilla Path from a list of nodes and an explicit end node.
     * Returns null if the node list is empty.
     *
     * The canReach flag is set to true — we assume the nodes represent a valid
     * walkable sequence (either from the global path or from refinement).
     */
    @Nullable
    private static Path pathFromNodes(final List<Node> nodes, final Node endNode) {
        if (nodes.isEmpty()) {
            return null;
        }
        return new Path(nodes, endNode.asBlockPos(), true);
    }

    // -------------------------------------------------------------------------
    // Internal data class
    // -------------------------------------------------------------------------

    /**
     * A raw (not yet reprojected) slice of the global path that belongs to one
     * coordinate space.
     */
    private static final class RawSegment {
        final @Nullable SubLevel subLevel; // null = global world
        final List<Node> nodes;            // global-space nodes
        final @Nullable Vec3 entryGlobal;  // global entry position (null for first segment)
        final @Nullable Vec3 exitGlobal;   // global exit position (null for last segment)

        RawSegment(
                @Nullable final SubLevel subLevel,
                final List<Node> nodes,
                @Nullable final Vec3 entryGlobal,
                @Nullable final Vec3 exitGlobal) {
            this.subLevel = subLevel;
            this.nodes = nodes;
            this.entryGlobal = entryGlobal;
            this.exitGlobal = exitGlobal;
        }
    }
}
