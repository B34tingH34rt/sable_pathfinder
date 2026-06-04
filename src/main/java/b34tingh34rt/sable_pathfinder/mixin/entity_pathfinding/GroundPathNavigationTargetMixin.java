package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import net.minecraft.core.BlockPos;
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

        if (!isActiveTarget) {
            return;
        }

        final BlockPos worldTarget = entity.blockPosition();
        final Path correctedPath = super.createPath(Set.of(worldTarget), 16, true, accuracy);
        final boolean usefulPath = correctedPath != null && (correctedPath.canReach() || correctedPath.getNodeCount() > 1);

        cir.setReturnValue(usefulPath ? correctedPath : null);
    }
}
