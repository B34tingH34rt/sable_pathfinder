package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.SablePathfinder;
import b34tingh34rt.sable_pathfinder.network.PathGizmoPayload;
import b34tingh34rt.sable_pathfinder.visualization.PathVisualizationState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelPathVisualizationMixin {
    private static final int SABLE_PATHFINDER$PATH_UPDATE_INTERVAL_TICKS = 5;
    private static final double SABLE_PATHFINDER$VIEW_DISTANCE = 96.0;
    private static final int SABLE_PATHFINDER$MAX_PATHS_PER_PLAYER = 96;
    private static boolean sablePathfinder$loggedFirstPathSend = false;

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void sablePathfinder$renderMobPaths(final BooleanSupplier shouldKeepTicking, final CallbackInfo ci) {
        if (!PathVisualizationState.isEnabled()) {
            return;
        }

        final ServerLevel level = (ServerLevel) (Object) this;
        if (level.getGameTime() % SABLE_PATHFINDER$PATH_UPDATE_INTERVAL_TICKS != 0L) {
            return;
        }

        for (final ServerPlayer player : level.players()) {
            final AABB area = player.getBoundingBox().inflate(SABLE_PATHFINDER$VIEW_DISTANCE);
            final var mobs = level.getEntitiesOfClass(Mob.class, area, mob -> mob.isAlive() && mob.getNavigation().getPath() != null && !mob.getNavigation().isDone());

            int sentPaths = 0;
            for (final Mob mob : mobs) {
                final Path path = mob.getNavigation().getPath();
                if (path == null || path.getNodeCount() < 1) {
                    continue;
                }

                final List<BlockPos> nodes = this.sablePathfinder$getVisiblePathNodes(path);
                if (nodes.isEmpty()) {
                    continue;
                }

                PacketDistributor.sendToPlayer(player, new PathGizmoPayload(mob.getId(), this.sablePathfinder$getColorForMob(mob), nodes));
                if (!sablePathfinder$loggedFirstPathSend) {
                    sablePathfinder$loggedFirstPathSend = true;
                    SablePathfinder.LOGGER.info("Sent path gizmo payload for {} to {} with {} nodes.", mob.getName().getString(), player.getName().getString(), nodes.size());
                }
                if (++sentPaths >= SABLE_PATHFINDER$MAX_PATHS_PER_PLAYER) {
                    break;
                }
            }
        }
    }

    private int sablePathfinder$getColorForMob(final Mob mob) {
        final int hash = mob.getUUID().hashCode();
        final float hue = (hash & 0xFFFFFF) / (float) 0xFFFFFF;
        return Mth.hsvToRgb(hue, 0.9f, 1.0f);
    }

    private List<BlockPos> sablePathfinder$getVisiblePathNodes(final Path path) {
        final int startNode = path.getNextNodeIndex();
        final int endNode = Math.min(path.getNodeCount(), startNode + PathGizmoPayload.MAX_NODES);
        final List<BlockPos> nodes = new ArrayList<>(Math.max(0, endNode - startNode));
        for (int i = startNode; i < endNode; i++) {
            nodes.add(path.getNode(i).asBlockPos());
        }
        return nodes;
    }
}
