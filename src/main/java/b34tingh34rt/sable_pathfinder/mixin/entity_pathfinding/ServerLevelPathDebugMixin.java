package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.debug.MobPathDebugState;
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
import java.util.Set;
import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelPathDebugMixin {
    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void sablePathfinder$renderMobPaths(final BooleanSupplier shouldKeepTicking, final CallbackInfo ci) {
        if (!MobPathDebugState.isEnabled()) {
            return;
        }

        final ServerLevel level = (ServerLevel) (Object) this;
        if ((level.getGameTime() & 1L) != 0L) {
            return;
        }

        final Set<Integer> renderedIds = new HashSet<>();

        for (final ServerPlayer player : level.players()) {
            final AABB area = player.getBoundingBox().inflate(96.0);
            final var mobs = level.getEntitiesOfClass(Mob.class, area, mob -> mob.isAlive() && mob.getNavigation().getPath() != null && !mob.getNavigation().isDone());

            for (final Mob mob : mobs) {
                if (!renderedIds.add(mob.getId())) {
                    continue;
                }

                final Path path = mob.getNavigation().getPath();
                if (path == null || path.getNodeCount() < 1) {
                    continue;
                }

                final DustParticleOptions particle = this.sablePathfinder$getParticleForMob(mob);
                Vec3 previous = mob.position().add(0.0, 0.15, 0.0);
                final int maxNodes = Math.min(path.getNodeCount(), 64);

                for (int i = path.getNextNodeIndex(); i < maxNodes; i++) {
                    final BlockPos nodePos = path.getNode(i).asBlockPos();
                    final Vec3 current = Vec3.atCenterOf(nodePos).add(0.0, 0.05, 0.0);
                    this.sablePathfinder$drawSegment(level, previous, current, particle);
                    previous = current;
                }
            }
        }
    }

    private DustParticleOptions sablePathfinder$getParticleForMob(final Mob mob) {
        final int hash = mob.getUUID().hashCode();
        final float hue = (hash & 0xFFFFFF) / (float) 0xFFFFFF;
        final int rgb = Mth.hsvToRgb(hue, 0.9f, 1.0f);
        final float r = ((rgb >> 16) & 0xFF) / 255.0f;
        final float g = ((rgb >> 8) & 0xFF) / 255.0f;
        final float b = (rgb & 0xFF) / 255.0f;
        return new DustParticleOptions(new Vector3f(r, g, b), 0.85f);
    }

    private void sablePathfinder$drawSegment(final ServerLevel level, final Vec3 start, final Vec3 end, final DustParticleOptions particle) {
        final Vec3 delta = end.subtract(start);
        final double length = delta.length();
        if (length < 0.001) {
            return;
        }

        final int steps = Math.max(2, Math.min(24, (int) (length * 4.0)));
        final Vec3 step = delta.scale(1.0 / steps);
        Vec3 pos = start;
        for (int i = 0; i <= steps; i++) {
            level.sendParticles(particle, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
            pos = pos.add(step);
        }
    }
}