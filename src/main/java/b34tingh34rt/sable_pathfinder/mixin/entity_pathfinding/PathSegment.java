package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One segment of a CompositePath. Each segment lives entirely in one coordinate
 * space — either the global world (subLevel == null) or a specific SubLevel.
 *
 * Nodes inside a sub-level segment are stored in that sub-level's LOCAL
 * coordinate space, not global space.
 *
 * entryGlobal / exitGlobal are the global-space positions of the boundary
 * crossing at the start and end of this segment. They are used for:
 *   - staleness detection (did the sub-level move away from where the path was planned?)
 *   - debug rendering (drawing transition markers in world space)
 *
 * Staleness is detected by comparing a reference point transformed through the
 * stored pose vs. the current pose. If the sub-level has translated or rotated
 * significantly, the transformed reference point will differ noticeably from
 * its original position.
 */
public final class PathSegment {

    /** The path in this segment's own coordinate space. Never null. */
    public final Path localPath;

    /**
     * The sub-level this segment lives in, or null if this segment is in the
     * global world.
     */
    @Nullable
    public final SubLevel subLevel;

    /**
     * A reference point in the sub-level's LOCAL space (the origin, Vec3.ZERO).
     * At compute time we record where this transforms to in global space.
     * If this global position drifts beyond the staleness threshold, the
     * segment is stale. Null when subLevel is null.
     */
    @Nullable
    private final Vec3 referenceGlobalAtComputeTime;

    /**
     * The global-space position at which the mob enters this segment.
     * Null for the first segment.
     */
    @Nullable
    public final Vec3 entryGlobal;

    /**
     * The global-space position at which the mob exits this segment.
     * Null for the last segment.
     */
    @Nullable
    public final Vec3 exitGlobal;

    public PathSegment(
            final Path localPath,
            @Nullable final SubLevel subLevel,
            @Nullable final Vec3 entryGlobal,
            @Nullable final Vec3 exitGlobal) {
        this.localPath = localPath;
        this.subLevel = subLevel;
        this.entryGlobal = entryGlobal;
        this.exitGlobal = exitGlobal;

        // Record where the sub-level's local origin maps to in global space
        // at the time this segment is created.
        if (subLevel != null) {
            this.referenceGlobalAtComputeTime = subLevel.logicalPose().transformPosition(Vec3.ZERO);
        } else {
            this.referenceGlobalAtComputeTime = null;
        }
    }

    /**
     * Returns true if this segment's sub-level has moved or rotated beyond the
     * given threshold since this segment was computed, making the local path
     * potentially invalid.
     *
     * Detection method: transform the local-space origin through the current
     * pose and compare it to where the origin was at compute time. A difference
     * greater than translationThreshold blocks indicates translation. Rotation
     * is detected by also transforming a unit-offset point and checking its
     * displacement relative to the origin shift.
     *
     * @param translationThreshold max allowed translation in blocks
     * @param rotationThresholdDeg max allowed rotation in degrees (approximate)
     */
    public boolean isStale(final double translationThreshold, final double rotationThresholdDeg) {
        if (this.subLevel == null || this.referenceGlobalAtComputeTime == null) {
            return false;
        }

        final Pose3d current = this.subLevel.logicalPose();

        // Check translation: compare where the local origin is now vs. at compute time
        final Vec3 currentOriginGlobal = current.transformPosition(Vec3.ZERO);
        final double translationDelta = currentOriginGlobal.distanceTo(this.referenceGlobalAtComputeTime);
        if (translationDelta > translationThreshold) {
            return true;
        }

        // Check rotation: transform a unit X point and compare its offset from
        // origin now vs. at compute time. If the offset direction has changed
        // significantly, rotation has occurred.
        final Vec3 unitX = new Vec3(1.0, 0.0, 0.0);
        final Vec3 currentUnitXGlobal = current.transformPosition(unitX).subtract(currentOriginGlobal);
        final Vec3 computedUnitXGlobal = current.transformPosition(unitX)
                .subtract(this.referenceGlobalAtComputeTime);

        // The angle between the two unit vectors approximates rotation magnitude.
        // Use dot product: cos(angle) = dot(a,b) / (|a||b|)
        // If cos(angle) < cos(threshold), rotation exceeded threshold.
        final double lenA = currentUnitXGlobal.length();
        final double lenB = computedUnitXGlobal.length();
        if (lenA > 0.001 && lenB > 0.001) {
            final double cosAngle = currentUnitXGlobal.dot(computedUnitXGlobal) / (lenA * lenB);
            final double cosThreshold = Math.cos(Math.toRadians(rotationThresholdDeg));
            if (cosAngle < cosThreshold) {
                return true;
            }
        }

        return false;
    }

    public boolean isGlobal() {
        return this.subLevel == null;
    }
}