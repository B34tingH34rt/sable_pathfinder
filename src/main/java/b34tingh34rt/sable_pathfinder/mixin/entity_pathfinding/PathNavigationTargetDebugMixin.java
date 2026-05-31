package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.debug.MobPathDebugState;
import b34tingh34rt.sable_pathfinder.debug.MobPathDebugState.Category;
import b34tingh34rt.sable_pathfinder.debug.PathNodeDebugState;
import b34tingh34rt.sable_pathfinder.debug.PathNodeSource;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Mixin(value = PathNavigation.class, priority = 2000)
public abstract class PathNavigationTargetDebugMixin {
    @Unique
    private static final int SABLE_PATHFINDER$MAX_TARGETS_IN_MESSAGE = 8;
    @Unique
    private static final int SABLE_PATHFINDER$MAX_PATH_NODES_IN_MESSAGE = 10;
    @Unique
    private static final int SABLE_PATHFINDER$MAX_REGION_EVIDENCE_NODES = 8;

    @Shadow
    @Final
    protected Mob mob;

    @Shadow
    @Final
    protected Level level;

    @Shadow
    protected Path path;

    @Shadow
    public abstract boolean moveTo(final Path pathentity, final double speed);

    @Shadow
    protected abstract Path createPath(final Set<BlockPos> targets, final int regionOffset, final boolean offsetUpward, final int accuracy);

    @Inject(method = "moveTo(Lnet/minecraft/world/entity/Entity;D)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sablePathfinder$debugMoveToEntitySource(final Entity entity, final double speed, final CallbackInfoReturnable<Boolean> cir) {
        if (this.level.isClientSide) {
            return;
        }

        final SubLevel mobTrackingSubLevel = Sable.HELPER.getTrackingSubLevel(this.mob);
        final SubLevel targetTrackingSubLevel = Sable.HELPER.getTrackingSubLevel(entity);
        final boolean isActiveTarget = entity == this.mob.getTarget();
        final boolean shouldUseWorldTarget = mobTrackingSubLevel == null && isActiveTarget;

        if (MobPathDebugState.isEnabled(Category.SOURCE)) {
            this.sablePathfinder$sendDebugMessage(
                    "[Sable Pathfinder] Path source moveTo(entity): " +
                            this.sablePathfinder$describeEntity(this.mob) +
                            " moving to " + this.sablePathfinder$describeAnyEntity(entity) +
                            " | speed " + this.sablePathfinder$formatNumber(speed) +
                            " | mob tracking " + this.sablePathfinder$describeSubLevel(mobTrackingSubLevel) +
                            ", mob containing " + this.sablePathfinder$describeSubLevel(Sable.HELPER.getContaining(this.level, this.mob.blockPosition())) +
                            this.sablePathfinder$describeEntitySubLevelState(entity) +
                            (shouldUseWorldTarget ? " | correcting entity target to world block" : "")
            );
        }

        if (!shouldUseWorldTarget) {
            return;
        }

        final BlockPos worldTarget = entity.blockPosition();
        final boolean forceFreshPathForRegionDebug = MobPathDebugState.isEnabled(Category.REGION);
        final Path previousPath = this.path;
        if (forceFreshPathForRegionDebug) {
            this.path = null;
        }

        final Path correctedPath = this.createPath(Set.of(worldTarget), 16, true, 1);
        final boolean hasUsefulPath = correctedPath != null && (correctedPath.canReach() || correctedPath.getNodeCount() > 1);
        final boolean moved = hasUsefulPath && this.moveTo(correctedPath, speed);
        if (!moved && forceFreshPathForRegionDebug) {
            this.path = previousPath;
        }

        if (MobPathDebugState.isEnabled(Category.CORRECTION)) {
            this.sablePathfinder$sendDebugMessage(
                    "[Sable Pathfinder] Corrected moveTo(entity) for " +
                            this.sablePathfinder$describeEntity(this.mob) +
                            ": target " + this.sablePathfinder$describeAnyEntity(entity) +
                            " is " + (targetTrackingSubLevel == null ? "not tracked" : "tracked by " + this.sablePathfinder$describeSubLevel(targetTrackingSubLevel)) +
                            ", mob is " + (mobTrackingSubLevel == null ? "not tracked" : "tracked by " + this.sablePathfinder$describeSubLevel(mobTrackingSubLevel)) +
                            ", using world target " + worldTarget.toShortString() +
                            " to bypass GroundPathNavigation entity/block rewrites | path " +
                            (correctedPath == null
                                    ? "none"
                                    : "target " + correctedPath.getTarget().toShortString() +
                                    ", end " + this.sablePathfinder$formatBlockPos(correctedPath.getEndNode() == null ? null : correctedPath.getEndNode().asBlockPos()) +
                                    ", can reach " + correctedPath.canReach() +
                                    ", nodes " + correctedPath.getNodeCount() +
                                    (hasUsefulPath ? "" : ", rejected one-node fallback")) +
                            ", moved " + moved +
                            (forceFreshPathForRegionDebug ? ", forced fresh path for region debug" : "")
            );
        }

        cir.setReturnValue(moved);
    }

    @Inject(method = "createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), require = 0)
    private void sablePathfinder$debugCreatePathEntitySource(final Entity entity, final int accuracy, final CallbackInfoReturnable<Path> cir) {
        if (!MobPathDebugState.isEnabled(Category.SOURCE) || this.level.isClientSide) {
            return;
        }

        this.sablePathfinder$sendDebugMessage(
                "[Sable Pathfinder] Path source createPath(entity): " +
                        this.sablePathfinder$describeEntity(this.mob) +
                        " creating path to " + this.sablePathfinder$describeAnyEntity(entity) +
                        " | vanilla would use block " + entity.blockPosition().toShortString() +
                        " | accuracy " + accuracy +
                        this.sablePathfinder$describeEntitySubLevelState(entity)
        );
    }

    @Inject(method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true, require = 0)
    private void sablePathfinder$debugCreatePathBlockSource(final BlockPos pos, final int accuracy, final CallbackInfoReturnable<Path> cir) {
        this.sablePathfinder$debugBlockSource("createPath(block, accuracy)", pos, accuracy, null);
        this.sablePathfinder$correctActiveTargetLocalBlock(pos, accuracy, cir);
    }

    @Inject(method = "createPath(Lnet/minecraft/core/BlockPos;II)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), require = 0)
    private void sablePathfinder$debugCreatePathBlockRegionSource(final BlockPos pos, final int regionOffset, final int accuracy,
                                                                  final CallbackInfoReturnable<Path> cir) {
        this.sablePathfinder$debugBlockSource("createPath(block, region, accuracy)", pos, accuracy, regionOffset);
    }

    @Inject(method = "createPath(Ljava/util/Set;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), require = 0)
    private void sablePathfinder$debugCreatePathSetSource(final Set<BlockPos> positions, final int distance, final CallbackInfoReturnable<Path> cir) {
        if (!MobPathDebugState.isEnabled(Category.SOURCE) || this.level.isClientSide) {
            return;
        }

        this.sablePathfinder$sendDebugMessage(
                "[Sable Pathfinder] Path source createPath(set, distance): " +
                        this.sablePathfinder$describeEntity(this.mob) +
                        " positions " + this.sablePathfinder$formatTargets(positions) +
                        " | distance " + distance +
                        this.sablePathfinder$describeActiveTargetForSource()
        );
    }

    @Inject(method = "createPath(Ljava/util/stream/Stream;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), require = 0)
    private void sablePathfinder$debugCreatePathStreamSource(final Stream<BlockPos> targets, final int accuracy, final CallbackInfoReturnable<Path> cir) {
        if (!MobPathDebugState.isEnabled(Category.SOURCE) || this.level.isClientSide) {
            return;
        }

        this.sablePathfinder$sendDebugMessage(
                "[Sable Pathfinder] Path source createPath(stream): " +
                        this.sablePathfinder$describeEntity(this.mob) +
                        " received a stream of target positions" +
                        " | accuracy " + accuracy +
                        this.sablePathfinder$describeActiveTargetForSource()
        );
    }

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), require = 0)
    private void sablePathfinder$debugPathTargets(final Set<BlockPos> targets, final int regionOffset, final boolean offsetUpward,
                                                  final int accuracy, final float followRange, final CallbackInfoReturnable<Path> cir) {
        PathNodeDebugState.beginCapture();

        if ((!MobPathDebugState.isEnabled(Category.TARGETS) && !MobPathDebugState.isEnabled(Category.REMAP)) || this.level.isClientSide) {
            return;
        }

        final LivingEntity targetEntity = this.mob.getTarget();
        final String entityTarget = targetEntity == null
                ? "no active mob target"
                : "active mob target " + this.sablePathfinder$describeEntity(targetEntity) +
                " at " + this.sablePathfinder$formatVec(targetEntity.position()) +
                " block " + targetEntity.blockPosition().toShortString();

        final String message = "[Sable Pathfinder] " +
                this.sablePathfinder$describeEntity(this.mob) +
                " at " + this.sablePathfinder$formatVec(this.mob.position()) +
                " requested path to " + targets.size() +
                " position(s): " + this.sablePathfinder$formatTargets(targets) +
                " | " + entityTarget +
                " | accuracy " + accuracy +
                ", range " + this.sablePathfinder$formatNumber(followRange) +
                ", search offset " + regionOffset +
                (offsetUpward ? ", starts one block higher" : "");

        if (MobPathDebugState.isEnabled(Category.TARGETS)) {
            this.level.players().forEach(player -> player.sendSystemMessage(Component.literal(message)));
        }
        if (MobPathDebugState.isEnabled(Category.REMAP)) {
            this.sablePathfinder$sendDebugMessage(this.sablePathfinder$describeSableRemap(targets, offsetUpward));
        }
    }

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("RETURN"), require = 0)
    private void sablePathfinder$debugActualPath(final Set<BlockPos> targets, final int regionOffset, final boolean offsetUpward,
                                                 final int accuracy, final float followRange, final CallbackInfoReturnable<Path> cir) {
        PathNodeDebugState.tagPath(cir.getReturnValue());

        if (this.level.isClientSide) {
            return;
        }

        final boolean showActualPath = MobPathDebugState.isEnabled(Category.ACTUAL_PATH);
        final boolean showRegion = MobPathDebugState.isEnabled(Category.REGION);
        if (!showActualPath && !showRegion) {
            return;
        }

        final Path path = cir.getReturnValue();
        if (path == null) {
            if (showActualPath) {
                this.sablePathfinder$sendDebugMessage(
                        "[Sable Pathfinder] " +
                                this.sablePathfinder$describeEntity(this.mob) +
                                " got no path for requested position(s): " +
                                this.sablePathfinder$formatTargets(targets)
                );
            }
            if (showRegion) {
                this.sablePathfinder$sendDebugMessage(
                        "[Sable Pathfinder] Region capture for " +
                                this.sablePathfinder$describeEntity(this.mob) +
                                ": " + PathNodeDebugState.describePathRegionEvidence(null, SABLE_PATHFINDER$MAX_REGION_EVIDENCE_NODES)
                );
            }
            return;
        }

        final BlockPos pathTarget = path.getTarget();
        final BlockPos endNode = path.getEndNode() == null ? null : path.getEndNode().asBlockPos();
        final String activeTargetComparison = this.sablePathfinder$formatActiveTargetComparison(pathTarget, endNode);

        if (showActualPath) {
            final String message = "[Sable Pathfinder] Actual path for " +
                    this.sablePathfinder$describeEntity(this.mob) +
                    ": path target " + this.sablePathfinder$formatBlockPos(pathTarget) +
                    ", end node " + this.sablePathfinder$formatBlockPos(endNode) +
                    ", can reach " + path.canReach() +
                    ", distance left " + this.sablePathfinder$formatNumber(path.getDistToTarget()) +
                    ", nodes " + path.getNodeCount() +
                    ", next index " + path.getNextNodeIndex() +
                    activeTargetComparison +
                    " | node preview: " + this.sablePathfinder$formatPathNodes(path);

            this.sablePathfinder$sendDebugMessage(message);
        }

        if (showRegion) {
            this.sablePathfinder$sendDebugMessage(
                    "[Sable Pathfinder] Region capture for " +
                            this.sablePathfinder$describeEntity(this.mob) +
                            ": " + PathNodeDebugState.describePathRegionEvidence(path, SABLE_PATHFINDER$MAX_REGION_EVIDENCE_NODES)
            );
        }
    }

    @Unique
    private String sablePathfinder$describeEntity(final LivingEntity entity) {
        return entity.getName().getString() +
                " (" + EntityType.getKey(entity.getType()) +
                " #" + entity.getId() + ")";
    }

    @Unique
    private String sablePathfinder$describeAnyEntity(final Entity entity) {
        return entity.getName().getString() +
                " (" + EntityType.getKey(entity.getType()) +
                " #" + entity.getId() + ")" +
                " at " + this.sablePathfinder$formatVec(entity.position()) +
                " block " + entity.blockPosition().toShortString();
    }

    @Unique
    private void sablePathfinder$debugBlockSource(final String source, final BlockPos pos, final int accuracy, final Integer regionOffset) {
        if (!MobPathDebugState.isEnabled(Category.SOURCE) || this.level.isClientSide) {
            return;
        }

        this.sablePathfinder$sendDebugMessage(
                "[Sable Pathfinder] Path source " + source + ": " +
                        this.sablePathfinder$describeEntity(this.mob) +
                        " received block " + pos.toShortString() +
                        " | accuracy " + accuracy +
                        (regionOffset == null ? "" : ", region offset " + regionOffset) +
                        this.sablePathfinder$describeBlockSubLevelProjection(pos) +
                        this.sablePathfinder$describeActiveTargetForSource()
        );
    }

    @Unique
    private String sablePathfinder$describeActiveTargetForSource() {
        final LivingEntity activeTarget = this.mob.getTarget();
        if (activeTarget == null) {
            return " | active target none";
        }

        return " | active target " + this.sablePathfinder$describeAnyEntity(activeTarget) +
                this.sablePathfinder$describeEntitySubLevelState(activeTarget);
    }

    @Unique
    private void sablePathfinder$correctActiveTargetLocalBlock(final BlockPos pos, final int accuracy, final CallbackInfoReturnable<Path> cir) {
        if (this.level.isClientSide || Sable.HELPER.getTrackingSubLevel(this.mob) != null) {
            return;
        }

        final LivingEntity activeTarget = this.mob.getTarget();
        if (activeTarget == null) {
            return;
        }

        final SubLevel containingSubLevel = Sable.HELPER.getContaining(this.level, pos);
        final SubLevel targetTrackingSubLevel = Sable.HELPER.getTrackingSubLevel(activeTarget);
        if (containingSubLevel == null || containingSubLevel != targetTrackingSubLevel) {
            return;
        }

        final BlockPos projectedWorldBlock = BlockPos.containing(containingSubLevel.logicalPose().transformPosition(pos.getCenter()));
        if (projectedWorldBlock.distManhattan(activeTarget.blockPosition()) > 2) {
            return;
        }

        final BlockPos worldTarget = activeTarget.blockPosition();
        final Path correctedPath = this.createPath(Set.of(worldTarget), 16, true, accuracy);
        final boolean usefulPath = correctedPath != null && (correctedPath.canReach() || correctedPath.getNodeCount() > 1);

        if (MobPathDebugState.isEnabled(Category.CORRECTION)) {
            this.sablePathfinder$sendDebugMessage(
                    "[Sable Pathfinder] Corrected local block path source for " +
                            this.sablePathfinder$describeEntity(this.mob) +
                            ": received target-local block " + pos.toShortString() +
                            " projecting to " + projectedWorldBlock.toShortString() +
                            ", using active target world block " + worldTarget.toShortString() +
                            " | path " +
                            (correctedPath == null
                                    ? "none"
                                    : "target " + correctedPath.getTarget().toShortString() +
                                    ", end " + this.sablePathfinder$formatBlockPos(correctedPath.getEndNode() == null ? null : correctedPath.getEndNode().asBlockPos()) +
                                    ", can reach " + correctedPath.canReach() +
                                    ", nodes " + correctedPath.getNodeCount() +
                                    (usefulPath ? "" : ", rejected one-node fallback"))
            );
        }

        cir.setReturnValue(usefulPath ? correctedPath : null);
    }

    @Unique
    private String sablePathfinder$describeEntitySubLevelState(final Entity entity) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(entity);
        final SubLevel containingSubLevel = Sable.HELPER.getContaining(this.level, entity.blockPosition());
        final StringBuilder builder = new StringBuilder();
        builder.append(" | tracking ")
                .append(this.sablePathfinder$describeSubLevel(trackingSubLevel))
                .append(", containing ")
                .append(this.sablePathfinder$describeSubLevel(containingSubLevel));

        if (trackingSubLevel != null) {
            final Vec3 projectedOut = trackingSubLevel.logicalPose().transformPosition(entity.position());
            final Vec3 projectedIn = trackingSubLevel.logicalPose().transformPositionInverse(entity.position());
            builder.append(", tracking pose projects current pos out to ")
                    .append(this.sablePathfinder$formatVec(projectedOut))
                    .append(" block ")
                    .append(BlockPos.containing(projectedOut).toShortString())
                    .append(", inverse to ")
                    .append(this.sablePathfinder$formatVec(projectedIn))
                    .append(" block ")
                    .append(BlockPos.containing(projectedIn).toShortString());
        }

        return builder.toString();
    }

    @Unique
    private String sablePathfinder$formatTargets(final Set<BlockPos> targets) {
        if (targets.isEmpty()) {
            return "none";
        }

        final StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (final BlockPos target : targets) {
            if (shown > 0) {
                builder.append("; ");
            }
            builder.append(target.toShortString());
            shown++;

            if (shown >= SABLE_PATHFINDER$MAX_TARGETS_IN_MESSAGE) {
                break;
            }
        }

        final int hidden = targets.size() - shown;
        if (hidden > 0) {
            builder.append("; +").append(hidden).append(" more");
        }

        return builder.toString();
    }

    @Unique
    private String sablePathfinder$formatActiveTargetComparison(final BlockPos pathTarget, final BlockPos endNode) {
        final LivingEntity targetEntity = this.mob.getTarget();
        if (targetEntity == null) {
            return "";
        }

        final BlockPos activeTargetBlock = targetEntity.blockPosition();
        return ", active target block " + activeTargetBlock.toShortString() +
                ", path target delta " + this.sablePathfinder$formatDelta(activeTargetBlock, pathTarget) +
                ", end delta " + this.sablePathfinder$formatDelta(activeTargetBlock, endNode);
    }

    @Unique
    private String sablePathfinder$describeSableRemap(final Set<BlockPos> targets, final boolean offsetUpward) {
        SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(this.mob);
        String trackingSource = "mob tracking";

        if (trackingSubLevel == null) {
            trackingSource = "target containment";
            for (final BlockPos target : targets) {
                trackingSubLevel = Sable.HELPER.getContaining(this.level, target);
                if (trackingSubLevel != null) {
                    break;
                }
            }
        }

        final LivingEntity activeTarget = this.mob.getTarget();
        final String activeTargetInfo = activeTarget == null
                ? "none"
                : this.sablePathfinder$describeEntity(activeTarget) +
                " tracking " + this.sablePathfinder$describeSubLevel(Sable.HELPER.getTrackingSubLevel(activeTarget)) +
                ", containing " + this.sablePathfinder$describeSubLevel(Sable.HELPER.getContaining(this.level, activeTarget.blockPosition()));

        if (trackingSubLevel == null) {
            return "[Sable Pathfinder] Sable remap for " +
                    this.sablePathfinder$describeEntity(this.mob) +
                    ": inactive, no mob tracking sub-level and no requested target is contained by a sub-level" +
                    " | mob containing " + this.sablePathfinder$describeSubLevel(Sable.HELPER.getContaining(this.level, this.mob.blockPosition())) +
                    " | active target " + activeTargetInfo +
                    " | requested target contexts: " + this.sablePathfinder$formatTargetContexts(targets, null);
        }

        final Vec3 localMobPosition = trackingSubLevel.logicalPose().transformPositionInverse(this.mob.position());
        final BlockPos localMobBlock = BlockPos.containing(localMobPosition);
        final BlockPos searchOrigin = offsetUpward ? localMobBlock.above() : localMobBlock;

        return "[Sable Pathfinder] Sable remap for " +
                this.sablePathfinder$describeEntity(this.mob) +
                ": active via " + trackingSource +
                ", tracking " + this.sablePathfinder$describeSubLevel(trackingSubLevel) +
                ", mob local " + this.sablePathfinder$formatVec(localMobPosition) +
                " block " + localMobBlock.toShortString() +
                ", search origin " + searchOrigin.toShortString() +
                " | active target " + activeTargetInfo +
                " | requested target contexts: " + this.sablePathfinder$formatTargetContexts(targets, trackingSubLevel) +
                " | Sable local target set: " + this.sablePathfinder$formatSableLocalTargets(targets, trackingSubLevel);
    }

    @Unique
    private String sablePathfinder$formatTargetContexts(final Set<BlockPos> targets, final SubLevel trackingSubLevel) {
        if (targets.isEmpty()) {
            return "none";
        }

        final StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (final BlockPos target : targets) {
            if (shown > 0) {
                builder.append("; ");
            }

            final SubLevel containing = Sable.HELPER.getContaining(this.level, target);
            builder.append(target.toShortString())
                    .append(" contained by ")
                    .append(this.sablePathfinder$describeSubLevel(containing));

            if (trackingSubLevel != null) {
                builder.append(containing == trackingSubLevel ? " (kept)" : " (inverse-transformed)");
            }

            shown++;
            if (shown >= SABLE_PATHFINDER$MAX_TARGETS_IN_MESSAGE) {
                break;
            }
        }

        final int hidden = targets.size() - shown;
        if (hidden > 0) {
            builder.append("; +").append(hidden).append(" more");
        }

        return builder.toString();
    }

    @Unique
    private String sablePathfinder$formatSableLocalTargets(final Set<BlockPos> targets, final SubLevel trackingSubLevel) {
        if (targets.isEmpty()) {
            return "none";
        }

        final StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (final BlockPos target : targets) {
            if (shown > 0) {
                builder.append("; ");
            }

            final SubLevel containing = Sable.HELPER.getContaining(this.level, target);
            final BlockPos localTarget = containing == trackingSubLevel
                    ? target
                    : BlockPos.containing(trackingSubLevel.logicalPose().transformPositionInverse(target.getCenter()));

            builder.append(target.toShortString())
                    .append(" -> ")
                    .append(localTarget.toShortString());

            shown++;
            if (shown >= SABLE_PATHFINDER$MAX_TARGETS_IN_MESSAGE) {
                break;
            }
        }

        final int hidden = targets.size() - shown;
        if (hidden > 0) {
            builder.append("; +").append(hidden).append(" more");
        }

        return builder.toString();
    }

    @Unique
    private String sablePathfinder$describeBlockSubLevelProjection(final BlockPos pos) {
        final SubLevel containingSubLevel = Sable.HELPER.getContaining(this.level, pos);
        if (containingSubLevel == null) {
            return " | block containing none";
        }

        final Vec3 projectedWorld = containingSubLevel.logicalPose().transformPosition(pos.getCenter());
        final BlockPos projectedWorldBlock = BlockPos.containing(projectedWorld);
        final LivingEntity activeTarget = this.mob.getTarget();

        return " | block containing " + this.sablePathfinder$describeSubLevel(containingSubLevel) +
                ", projects to world " + this.sablePathfinder$formatVec(projectedWorld) +
                " block " + projectedWorldBlock.toShortString() +
                (activeTarget == null
                        ? ""
                        : ", projected delta to active target " + this.sablePathfinder$formatDelta(activeTarget.blockPosition(), projectedWorldBlock));
    }

    @Unique
    private String sablePathfinder$describeSubLevel(final SubLevel subLevel) {
        if (subLevel == null) {
            return "none";
        }

        final String name = subLevel.getName();
        final String id = subLevel.getUniqueId() == null
                ? "no-id"
                : subLevel.getUniqueId().toString().substring(0, 8);

        return (name == null ? "unnamed" : name) + "/" + id;
    }

    @Unique
    private String sablePathfinder$formatPathNodes(final Path path) {
        if (path.getNodeCount() < 1) {
            return "none";
        }

        final StringBuilder builder = new StringBuilder();
        final int shown = Math.min(path.getNodeCount(), SABLE_PATHFINDER$MAX_PATH_NODES_IN_MESSAGE);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                builder.append(" -> ");
            }
            if (i == path.getNextNodeIndex()) {
                builder.append("[next] ");
            }
            builder.append(path.getNode(i).asBlockPos().toShortString())
                    .append(" ")
                    .append(this.sablePathfinder$formatNodeSource(path.getNode(i)));
        }

        final int hidden = path.getNodeCount() - shown;
        if (hidden > 0) {
            builder.append(" -> +").append(hidden).append(" more");
        }

        return builder.toString();
    }

    @Unique
    private String sablePathfinder$formatNodeSource(final net.minecraft.world.level.pathfinder.Node node) {
        final PathNodeSource source = PathNodeDebugState.sourceFor(node);
        return source == null ? "(world/unknown)" : "(" + source.shortLabel() + ")";
    }

    @Unique
    private String sablePathfinder$formatDelta(final BlockPos from, final BlockPos to) {
        if (to == null) {
            return "none";
        }

        return "x " + (to.getX() - from.getX()) +
                ", y " + (to.getY() - from.getY()) +
                ", z " + (to.getZ() - from.getZ());
    }

    @Unique
    private String sablePathfinder$formatBlockPos(final BlockPos pos) {
        return pos == null ? "none" : pos.toShortString();
    }

    @Unique
    private String sablePathfinder$formatVec(final Vec3 vec) {
        return this.sablePathfinder$formatNumber(vec.x) +
                ", " + this.sablePathfinder$formatNumber(vec.y) +
                ", " + this.sablePathfinder$formatNumber(vec.z);
    }

    @Unique
    private String sablePathfinder$formatNumber(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Unique
    private void sablePathfinder$sendDebugMessage(final String message) {
        this.level.players().forEach(player -> player.sendSystemMessage(Component.literal(message)));
    }
}
