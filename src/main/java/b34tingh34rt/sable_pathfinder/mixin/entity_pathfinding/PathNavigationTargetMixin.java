package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(value = PathNavigation.class, priority = 2000)
public abstract class PathNavigationTargetMixin {
    @Shadow
    @Final
    protected Mob mob;

    @Shadow
    @Final
    protected Level level;

    @Shadow
    public abstract boolean moveTo(final Path pathentity, final double speed);

    @Shadow
    protected abstract Path createPath(final Set<BlockPos> targets, final int regionOffset, final boolean offsetUpward, final int accuracy);

    @Inject(method = "moveTo(Lnet/minecraft/world/entity/Entity;D)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sablePathfinder$moveToWorldTarget(final Entity entity, final double speed, final CallbackInfoReturnable<Boolean> cir) {
        if (this.level.isClientSide) {
            return;
        }

        final SubLevel mobTrackingSubLevel = Sable.HELPER.getTrackingSubLevel(this.mob);
        final boolean shouldUseWorldTarget = mobTrackingSubLevel == null && entity == this.mob.getTarget();
        if (!shouldUseWorldTarget) {
            return;
        }

        final Path correctedPath = this.createPath(Set.of(entity.blockPosition()), 16, true, 1);
        final boolean moved = this.sablePathfinder$hasUsefulPath(correctedPath) && this.moveTo(correctedPath, speed);
        cir.setReturnValue(moved);
    }

    @Inject(method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true, require = 0)
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

        final Path correctedPath = this.createPath(Set.of(activeTarget.blockPosition()), 16, true, accuracy);
        cir.setReturnValue(this.sablePathfinder$hasUsefulPath(correctedPath) ? correctedPath : null);
    }

    private boolean sablePathfinder$hasUsefulPath(final Path path) {
        return path != null && (path.canReach() || path.getNodeCount() > 1);
    }
}
