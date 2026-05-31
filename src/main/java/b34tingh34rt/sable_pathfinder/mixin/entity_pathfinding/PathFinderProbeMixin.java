package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.debug.PathNodeDebugState;
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

@Mixin(PathFinder.class)
public abstract class PathFinderProbeMixin {
    @Inject(method = "findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"))
    private void sablePathfinder$findPathHead(final PathNavigationRegion region, final Mob mob, final Set<BlockPos> targets,
                                              final float maxRange, final int accuracy, final float searchDepthMultiplier,
                                              final CallbackInfoReturnable<Path> cir) {
        PathNodeDebugState.recordProbe("PathFinder#findPath HEAD", mob.blockPosition(),
                "mob=" + mob.getType().builtInRegistryHolder().key().location() +
                        ", targets=" + targets.size() +
                        ", range=" + maxRange +
                        ", accuracy=" + accuracy);
    }

    @Inject(method = "findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("RETURN"))
    private void sablePathfinder$findPathReturn(final PathNavigationRegion region, final Mob mob, final Set<BlockPos> targets,
                                                final float maxRange, final int accuracy, final float searchDepthMultiplier,
                                                final CallbackInfoReturnable<Path> cir) {
        final Path path = cir.getReturnValue();
        PathNodeDebugState.recordProbe("PathFinder#findPath RETURN", mob.blockPosition(),
                path == null
                        ? "path=null"
                        : "path nodes=" + path.getNodeCount() + ", canReach=" + path.canReach());
    }
}
