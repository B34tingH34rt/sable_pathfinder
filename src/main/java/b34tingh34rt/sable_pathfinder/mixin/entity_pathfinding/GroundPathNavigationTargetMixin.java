package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.debug.MobPathDebugState;
import b34tingh34rt.sable_pathfinder.debug.MobPathDebugState.Category;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(value = GroundPathNavigation.class, priority = 2000)
public abstract class GroundPathNavigationTargetMixin extends PathNavigation {
    public GroundPathNavigationTargetMixin(final Mob mob, final Level level) {
        super(mob, level);
    }

    @Inject(method = "createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true, require = 0)
    private void sablePathfinder$correctEntityProbePath(final Entity entity, final int accuracy, final CallbackInfoReturnable<Path> cir) {
        final Mob mob = ((PathNavigationMobAccessor) this).sablePathfinder$getMob();
        if (this.level.isClientSide) {
            return;
        }

        final Entity activeTarget = mob.getTarget();
        final boolean isActiveTarget = activeTarget != null
                && (entity == activeTarget || entity.getId() == activeTarget.getId() || entity.getUUID().equals(activeTarget.getUUID()));

        if (MobPathDebugState.isEnabled(Category.SOURCE)) {
            this.level.players().forEach(player -> player.sendSystemMessage(Component.literal(
                    "[Sable Pathfinder] GroundPathNavigation entity probe seen for " +
                            mob.getName().getString() +
                            " -> " + entity.getName().getString() +
                            " | active target match " + isActiveTarget +
                            " | accuracy " + accuracy
            )));
        }

        if (!isActiveTarget) {
            return;
        }

        final BlockPos worldTarget = entity.blockPosition();
        final Path correctedPath = super.createPath(Set.of(worldTarget), 16, true, accuracy);
        final boolean usefulPath = correctedPath != null && (correctedPath.canReach() || correctedPath.getNodeCount() > 1);

        if (MobPathDebugState.isEnabled(Category.CORRECTION)) {
            final String pathText = correctedPath == null
                    ? "none"
                    : "target " + correctedPath.getTarget().toShortString() +
                    ", nodes " + correctedPath.getNodeCount() +
                    ", can reach " + correctedPath.canReach() +
                    (usefulPath ? "" : ", rejected one-node fallback");

            this.level.players().forEach(player -> player.sendSystemMessage(Component.literal(
                    "[Sable Pathfinder] Corrected GroundPathNavigation entity probe for " +
                            mob.getName().getString() +
                            " -> " + entity.getName().getString() +
                            ": using world target " + worldTarget.toShortString() +
                            " | path " + pathText
            )));
        }

        cir.setReturnValue(usefulPath ? correctedPath : null);
    }
}
