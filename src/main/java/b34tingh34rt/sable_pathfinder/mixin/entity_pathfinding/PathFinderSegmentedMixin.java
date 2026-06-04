package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.path.PathProjectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(value = PathFinder.class, priority = 2000)
public abstract class PathFinderSegmentedMixin {
    @Inject(method = "findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"))
    private void sablePathfinder$beginSegmentedPathCapture(
            final PathNavigationRegion region,
            final Mob mob,
            final Set<BlockPos> targetPositions,
            final float maxRange,
            final int accuracy,
            final float searchDepthMultiplier,
            final CallbackInfoReturnable<Path> cir
    ) {
        PathProjectionContext.begin(mob.level(), mob);
    }

    @Inject(method = "findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("RETURN"))
    private void sablePathfinder$finishSegmentedPathCapture(
            final PathNavigationRegion region,
            final Mob mob,
            final Set<BlockPos> targetPositions,
            final float maxRange,
            final int accuracy,
            final float searchDepthMultiplier,
            final CallbackInfoReturnable<Path> cir
    ) {
        try {
            PathProjectionContext.attachToPath(cir.getReturnValue());
        } finally {
            PathProjectionContext.end();
        }
    }
}
