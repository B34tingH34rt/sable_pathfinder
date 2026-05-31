package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.debug.PathNodeDebugState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathfindingContext.class)
public abstract class PathfindingContextPathTypeProbeMixin {
    @Inject(method = "getPathTypeFromState", at = @At("RETURN"))
    private void sablePathfinder$getPathTypeFromState(final int x, final int y, final int z, final CallbackInfoReturnable<PathType> cir) {
        PathNodeDebugState.recordProbe("PathfindingContext#getPathTypeFromState", new BlockPos(x, y, z), "type=" + cir.getReturnValue());
    }
}
