package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.debug.MobPathDebugState;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Debug renderer for mob paths, extended to visualise CompositePath segments.
 *
 * Rendering conventions:
 *   - Each segment of a CompositePath is drawn in the mob's UUID-derived hue,
 *     but with saturation reduced for sub-level segments so they visually
 *     differ from global segments.
 *   - Transition points (entry/exit of sub-level boundaries) are rendered as
 *     a small burst of white particles.
 *   - Staleness: if a CompositePath is stale, its segments are rendered in a
 *     washed-out grey to indicate the path is about to be invalidated.
 *
 * Coordinate space note:
 *   Sub-level segment nodes are stored in local space. To render them in the
 *   world, each node position is transformed back to global space using the
 *   sub-level's current logical pose (transformPosition).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelPathDebugMixin {

    // Dust particle size for normal nodes
    private static final float PARTICLE_SIZE_NORMAL = 0.85f;
    // Dust particle size for transition markers
    private static final float PARTICLE_SIZE_TRANSITION = 1.4f;

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void sablePathfinder$renderMobPaths(
            final BooleanSupplier shouldKeepTicking,
            final CallbackInfo ci) {

        if (!MobPathDebugState.isEnabled()) return;

        final ServerLevel level = (ServerLevel) (Object) this;
        if ((level.getGameTime() & 1L) != 0L) return;

        final Set<Integer> renderedIds = new HashSet<>();

        for (final ServerPlayer player : level.players()) {
            final AABB area = player.getBoundingBox().inflate(96.0);
            final var mobs = level.getEntitiesOfClass(
                    Mob.class, area,
                    mob -> mob.isAlive()
                            && mob.getNavigation().getPath() != null
                            && !mob.getNavigation().isDone()
            );

            for (final Mob mob : mobs) {
                if (!renderedIds.add(mob.getId())) continue;

                final Path path = mob.getNavigation().getPath();
                if (path == null) continue;

                if (path instanceof final CompositePath composite) {
                    this.sablePathfinder$renderCompositePath(level, mob, composite);
                } else {
                    // Plain vanilla / original single-segment path
                    this.sablePathfinder$renderPlainPath(level, mob, path);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Composite path rendering
    // -------------------------------------------------------------------------

    private void sablePathfinder$renderCompositePath(
            final ServerLevel level,
            final Mob mob,
            final CompositePath composite) {

        final boolean stale = composite.isStale();
        final DustParticleOptions mobColor = this.sablePathfinder$getParticleForMob(mob, 0.9f);
        final DustParticleOptions subLevelColor = this.sablePathfinder$getParticleForMob(mob, 0.4f);
        final DustParticleOptions staleColor = new DustParticleOptions(new Vector3f(0.5f, 0.5f, 0.5f), PARTICLE_SIZE_NORMAL);
        final DustParticleOptions transitionColor = new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), PARTICLE_SIZE_TRANSITION);

        final List<PathSegment> segments = composite.segments();

        for (int s = 0; s < segments.size(); s++) {
            final PathSegment segment = segments.get(s);
            final Path localPath = segment.localPath;
            if (localPath.getNodeCount() < 1) continue;

            // Pick color for this segment
            final DustParticleOptions segmentColor = stale ? staleColor
                    : segment.isGlobal() ? mobColor
                    : subLevelColor;

            // Determine the starting position for the first node of this segment
            Vec3 previous = s == 0
                    ? mob.position().add(0.0, 0.15, 0.0)
                    : this.sablePathfinder$nodeToGlobal(
                            localPath.getNode(localPath.getNextNodeIndex()),
                            segment);

            final int maxNodes = Math.min(localPath.getNodeCount(), 64);
            for (int i = Math.max(0, localPath.getNextNodeIndex()); i < maxNodes; i++) {
                final Vec3 current = this.sablePathfinder$nodeToGlobal(localPath.getNode(i), segment)
                        .add(0.0, 0.05, 0.0);
                this.sablePathfinder$drawSegmentLine(level, previous, current, segmentColor);
                previous = current;
            }

            // Draw transition marker at the exit point of this segment
            // (which is the entry of the next segment)
            if (segment.exitGlobal != null) {
                this.sablePathfinder$drawTransitionMarker(level, segment.exitGlobal, transitionColor);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Plain path rendering (unchanged from original)
    // -------------------------------------------------------------------------

    private void sablePathfinder$renderPlainPath(
            final ServerLevel level,
            final Mob mob,
            final Path path) {

        if (path.getNodeCount() < 1) return;

        final DustParticleOptions particle = this.sablePathfinder$getParticleForMob(mob, 0.9f);
        Vec3 previous = mob.position().add(0.0, 0.15, 0.0);
        final int maxNodes = Math.min(path.getNodeCount(), 64);

        for (int i = path.getNextNodeIndex(); i < maxNodes; i++) {
            final BlockPos nodePos = path.getNode(i).asBlockPos();
            final Vec3 current = Vec3.atCenterOf(nodePos).add(0.0, 0.05, 0.0);
            this.sablePathfinder$drawSegmentLine(level, previous, current, particle);
            previous = current;
        }
    }

    // -------------------------------------------------------------------------
    // Coordinate helpers
    // -------------------------------------------------------------------------

    /**
     * Transform a node position from the segment's local space to global space
     * for rendering. Global-space segments are returned as-is.
     */
    private Vec3 sablePathfinder$nodeToGlobal(
            final net.minecraft.world.level.pathfinder.Node node,
            final PathSegment segment) {

        final Vec3 localCenter = Vec3.atCenterOf(node.asBlockPos());
        if (segment.isGlobal()) {
            return localCenter;
        }
        // Transform from sub-level local space to global space
        final Pose3d pose = segment.subLevel.logicalPose();
        return pose.transformPosition(localCenter);
    }

    // -------------------------------------------------------------------------
    // Particle helpers
    // -------------------------------------------------------------------------

    /**
     * Get a colored dust particle for a mob, derived from its UUID hash.
     * @param saturation HSV saturation — lower values produce more washed-out colors.
     */
    private DustParticleOptions sablePathfinder$getParticleForMob(
            final Mob mob,
            final float saturation) {

        final int hash = mob.getUUID().hashCode();
        final float hue = (hash & 0xFFFFFF) / (float) 0xFFFFFF;
        final int rgb = Mth.hsvToRgb(hue, saturation, 1.0f);
        final float r = ((rgb >> 16) & 0xFF) / 255.0f;
        final float g = ((rgb >> 8) & 0xFF) / 255.0f;
        final float b = (rgb & 0xFF) / 255.0f;
        return new DustParticleOptions(new Vector3f(r, g, b), PARTICLE_SIZE_NORMAL);
    }

    /**
     * Draw an interpolated line of dust particles between two points.
     */
    private void sablePathfinder$drawSegmentLine(
            final ServerLevel level,
            final Vec3 start,
            final Vec3 end,
            final DustParticleOptions particle) {

        final Vec3 delta = end.subtract(start);
        final double length = delta.length();
        if (length < 0.001) return;

        final int steps = Math.max(2, Math.min(24, (int) (length * 4.0)));
        final Vec3 step = delta.scale(1.0 / steps);
        Vec3 pos = start;
        for (int i = 0; i <= steps; i++) {
            level.sendParticles(particle, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
            pos = pos.add(step);
        }
    }

    /**
     * Draw a small burst of particles at a transition point to mark a
     * boundary crossing between coordinate spaces.
     */
    private void sablePathfinder$drawTransitionMarker(
            final ServerLevel level,
            final Vec3 pos,
            final DustParticleOptions particle) {

        // Draw a small cross pattern at the transition point
        final double r = 0.3;
        final double[][] offsets = {
            {r, 0, 0}, {-r, 0, 0},
            {0, r, 0}, {0, -r, 0},
            {0, 0, r}, {0, 0, -r}
        };
        for (final double[] offset : offsets) {
            level.sendParticles(
                    particle,
                    pos.x + offset[0],
                    pos.y + offset[1],
                    pos.z + offset[2],
                    1, 0.0, 0.0, 0.0, 0.0
            );
        }
    }
}
