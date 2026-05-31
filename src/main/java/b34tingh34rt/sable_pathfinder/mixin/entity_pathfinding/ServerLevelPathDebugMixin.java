package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.debug.MobPathDebugState;
import b34tingh34rt.sable_pathfinder.debug.MobPathDebugState.Category;
import b34tingh34rt.sable_pathfinder.debug.PathNodeDebugState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelPathDebugMixin {
    @Unique
    private final Map<String, Display.TextDisplay> sablePathfinder$nodeLabels = new HashMap<>();

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void sablePathfinder$renderMobPaths(final BooleanSupplier shouldKeepTicking, final CallbackInfo ci) {
        final boolean showParticles = MobPathDebugState.isEnabled(Category.PARTICLES);
        final boolean showNodeLabels = MobPathDebugState.isEnabled(Category.NODE_LABELS);
        final ServerLevel level = (ServerLevel) (Object) this;
        if (!showParticles && !showNodeLabels) {
            this.sablePathfinder$clearNodeLabels(level);
            return;
        }

        if ((level.getGameTime() & 3L) != 0L) {
            return;
        }

        final Set<Integer> renderedIds = new HashSet<>();
        final Set<String> activeLabels = new HashSet<>();
        int foundMobs = 0;
        int activePaths = 0;
        int consideredNodes = 0;
        int visibleNodes = 0;
        int createdLabels = 0;

        for (final ServerPlayer player : level.players()) {
            final AABB area = player.getBoundingBox().inflate(96.0);
            final var mobs = level.getEntitiesOfClass(Mob.class, area, mob -> mob.isAlive() && mob.getNavigation().getPath() != null && !mob.getNavigation().isDone());
            foundMobs += mobs.size();

            for (final Mob mob : mobs) {
                if (!renderedIds.add(mob.getId())) {
                    continue;
                }

                final Path path = mob.getNavigation().getPath();
                if (path == null || path.getNodeCount() < 1) {
                    continue;
                }
                activePaths++;

                Vec3 previous = mob.position().add(0.0, 0.15, 0.0);
                final int maxNodes = Math.min(path.getNodeCount(), 32);
                final DustParticleOptions particle = this.sablePathfinder$getParticleForMob(mob);

                for (int i = path.getNextNodeIndex(); i < maxNodes; i++) {
                    consideredNodes++;
                    final Vec3 current = PathNodeDebugState.displayPosFor(path.getNode(i)).add(0.0, 0.05, 0.0);
                    if (!this.sablePathfinder$isNearAnyPlayer(level, current, 96.0)) {
                        continue;
                    }
                    visibleNodes++;

                    if (showParticles) {
                        this.sablePathfinder$drawSegment(level, previous, current, particle);
                    }
                    if (showNodeLabels && i < path.getNextNodeIndex() + 12) {
                        final String key = mob.getId() + ":" + i;
                        activeLabels.add(key);
                        if (this.sablePathfinder$updateNodeLabel(level, key, current, PathNodeDebugState.labelFor(path.getNode(i), i))) {
                            createdLabels++;
                        }
                    }
                    previous = current;
                }
            }
        }

        if (showNodeLabels) {
            this.sablePathfinder$discardInactiveNodeLabels(activeLabels);
            this.sablePathfinder$sendVisualPassDebug(level, foundMobs, activePaths, consideredNodes, visibleNodes, activeLabels.size(), createdLabels);
        }
    }

    @Unique
    private boolean sablePathfinder$isNearAnyPlayer(final ServerLevel level, final Vec3 pos, final double maxDistance) {
        final double maxDistanceSqr = maxDistance * maxDistance;
        for (final ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) <= maxDistanceSqr) {
                return true;
            }
        }

        return false;
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
        if (length > 96.0) {
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

    @Unique
    private boolean sablePathfinder$updateNodeLabel(final ServerLevel level, final String key, final Vec3 nodePos, final String text) {
        boolean created = false;
        Display.TextDisplay label = this.sablePathfinder$nodeLabels.get(key);
        if (label == null || label.isRemoved()) {
            label = EntityType.TEXT_DISPLAY.create(level);
            if (label == null) {
                return false;
            }

            label.addTag("sable_pathfinder_node_label");
            label.setNoGravity(true);
            label.setSilent(true);
            label.setInvulnerable(true);
            ((TextDisplayAccessor) label).sablePathfinder$setLineWidth(160);
            ((TextDisplayAccessor) label).sablePathfinder$setBackgroundColor(0x40000000);
            this.sablePathfinder$nodeLabels.put(key, label);
            level.addFreshEntity(label);
            created = true;
        }

        ((TextDisplayAccessor) label).sablePathfinder$setText(Component.literal(text));
        label.setPos(nodePos.x, nodePos.y + 0.2, nodePos.z);
        return created;
    }

    @Unique
    private void sablePathfinder$sendVisualPassDebug(
            final ServerLevel level,
            final int foundMobs,
            final int activePaths,
            final int consideredNodes,
            final int visibleNodes,
            final int activeLabels,
            final int createdLabels
    ) {
        if (level.getGameTime() % 40L != 0L) {
            return;
        }

        final Component message = Component.literal(String.format(
                "[Sable Pathfinder] Path visual pass: players %d, mobs with paths %d, active paths %d, nodes checked %d, visible nodes %d, labels active %d, labels created %d",
                level.players().size(),
                foundMobs,
                activePaths,
                consideredNodes,
                visibleNodes,
                activeLabels,
                createdLabels
        ));
        for (final ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }

    @Unique
    private void sablePathfinder$discardInactiveNodeLabels(final Set<String> activeLabels) {
        final Iterator<Map.Entry<String, Display.TextDisplay>> iterator = this.sablePathfinder$nodeLabels.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, Display.TextDisplay> entry = iterator.next();
            if (!activeLabels.contains(entry.getKey()) || entry.getValue().isRemoved()) {
                entry.getValue().discard();
                iterator.remove();
            }
        }
    }

    @Unique
    private void sablePathfinder$clearNodeLabels(final ServerLevel level) {
        for (final Display.TextDisplay label : this.sablePathfinder$nodeLabels.values()) {
            label.discard();
        }

        this.sablePathfinder$nodeLabels.clear();

        for (final ServerPlayer player : level.players()) {
            final AABB area = player.getBoundingBox().inflate(128.0);
            for (final ArmorStand label : level.getEntitiesOfClass(ArmorStand.class, area,
                    entity -> entity.getTags().contains("sable_pathfinder_node_label"))) {
                label.discard();
            }
            for (final Display.TextDisplay label : level.getEntitiesOfClass(Display.TextDisplay.class, area,
                    entity -> entity.getTags().contains("sable_pathfinder_node_label"))) {
                label.discard();
            }
        }
    }
}
