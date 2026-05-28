package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.path.SableMixedPathFinder;
import net.minecraft.core.BlockPos;
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
public abstract class PathNavigationMixin {
    @Shadow
    @Final
    protected Mob mob;

    @Shadow
    @Final
    protected Level level;

    @Shadow
    private BlockPos targetPos;

    @Shadow
    private int reachRange;

    @Shadow
    protected abstract boolean canUpdatePath();

    @Shadow
    protected abstract void resetStuckTimeout();

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void sablePathfinder$createPath(final Set<BlockPos> targets, final int regionOffset, final boolean offsetUpward, final int accuracy, final float followRange, final CallbackInfoReturnable<Path> cir) {
        if (targets.isEmpty() || !this.canUpdatePath()) {
            return;
        }

        if (!SableMixedPathFinder.hasRelevantSubLevel(this.level, this.mob, targets, followRange + regionOffset)) {
            return;
        }

        final Path path = SableMixedPathFinder.findPath(this.level, this.mob, targets, followRange, accuracy);
        if (path == null || path.getTarget() == null || !path.canReach()) {
            return;
        }

        this.targetPos = path.getTarget();
        this.reachRange = accuracy;
        this.resetStuckTimeout();
        cir.setReturnValue(path);
    }
}
