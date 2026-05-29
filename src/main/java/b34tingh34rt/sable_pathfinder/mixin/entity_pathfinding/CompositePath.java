package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A path composed of multiple segments, each in its own coordinate space.
 *
 * Extends vanilla Path and delegates all calls to the currently active
 * segment's localPath so vanilla PathNavigation code works without modification.
 *
 * The super-constructor is called with a dummy empty node list and the final
 * segment's target/canReach, then syncFromCurrentSegment() immediately
 * overwrites the superclass node list via PathAccessor (since nodes is private
 * in vanilla Path and cannot be passed directly in the super() call from
 * outside the package).
 */
public final class CompositePath extends Path {

    public static final double STALE_TRANSLATION = 1.5;
    public static final double STALE_ROTATION_DEG = 10.0;

    private final List<PathSegment> segments;
    private int currentSegmentIndex;

    public CompositePath(final List<PathSegment> segments) {
        // Pass a dummy empty list and the last segment's target/canReach.
        // syncFromCurrentSegment() overwrites the node list immediately after.
        super(
                new ArrayList<>(),
                segments.get(segments.size() - 1).localPath.getTarget(),
                segments.get(segments.size() - 1).localPath.canReach()
        );
        this.segments = segments;
        this.currentSegmentIndex = 0;
        this.syncFromCurrentSegment();
    }

    // -------------------------------------------------------------------------
    // Segment management
    // -------------------------------------------------------------------------

    public PathSegment currentSegment() {
        return this.segments.get(this.currentSegmentIndex);
    }

    public boolean isLastSegment() {
        return this.currentSegmentIndex >= this.segments.size() - 1;
    }

    public int segmentCount() {
        return this.segments.size();
    }

    public List<PathSegment> segments() {
        return this.segments;
    }

    public boolean advanceSegment() {
        if (this.isLastSegment()) {
            return false;
        }
        this.currentSegmentIndex++;
        this.syncFromCurrentSegment();
        return true;
    }

    /**
     * Sync superclass private fields via PathAccessor so vanilla delegation
     * methods see the current segment's node list and progress pointer.
     */
    private void syncFromCurrentSegment() {
        final Path active = this.currentSegment().localPath;
        final PathAccessor self = (PathAccessor) (Object) this;
        self.sablePathfinder$setNodes(((PathAccessor) (Object) active).sablePathfinder$getNodes());
        self.sablePathfinder$setNextNodeIndex(active.getNextNodeIndex());
    }

    // -------------------------------------------------------------------------
    // Staleness
    // -------------------------------------------------------------------------

    public boolean isStale() {
        for (final PathSegment segment : this.segments) {
            if (segment.isStale(STALE_TRANSLATION, STALE_ROTATION_DEG)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Vanilla Path delegation
    // -------------------------------------------------------------------------

    @Override
    public boolean isDone() {
        if (!this.isLastSegment()) {
            return false;
        }
        return this.currentSegment().localPath.isDone();
    }

    @Override
    public boolean canReach() {
        return this.segments.get(this.segments.size() - 1).localPath.canReach();
    }

    @Override
    public BlockPos getTarget() {
        return this.segments.get(this.segments.size() - 1).localPath.getTarget();
    }

    @Override
    public float getDistToTarget() {
        return this.currentSegment().localPath.getDistToTarget();
    }

    @Override
    public int getNextNodeIndex() {
        return this.currentSegment().localPath.getNextNodeIndex();
    }

    @Override
    public void setNextNodeIndex(final int index) {
        this.currentSegment().localPath.setNextNodeIndex(index);
        ((PathAccessor) (Object) this).sablePathfinder$setNextNodeIndex(index);
    }

    @Override
    public int getNodeCount() {
        return this.currentSegment().localPath.getNodeCount();
    }

    @Override
    public Node getNode(final int index) {
        return this.currentSegment().localPath.getNode(index);
    }

    @Override
    public Vec3 getEntityPosAtNode(final net.minecraft.world.entity.Entity entity, final int index) {
        return this.currentSegment().localPath.getEntityPosAtNode(entity, index);
    }

    @Override
    public Vec3 getNextEntityPos(final net.minecraft.world.entity.Entity entity) {
        return this.currentSegment().localPath.getNextEntityPos(entity);
    }

    @Override
    public boolean notStarted() {
        return this.currentSegment().localPath.notStarted();
    }

    @Override
    public Node getEndNode() {
        return this.segments.get(this.segments.size() - 1).localPath.getEndNode();
    }

    @Override
    public boolean sameAs(@Nullable final Path other) {
        if (other == this) return true;
        if (other == null) return false;
        return this.getTarget().equals(other.getTarget());
    }
}