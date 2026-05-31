package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.debug.PathNodeDebugState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorProbeMixin {
    @Inject(method = "getPathTypeFromState", at = @At("RETURN"))
    private static void sablePathfinder$getPathTypeFromState(final BlockGetter level, final BlockPos pos, final CallbackInfoReturnable<PathType> cir) {
        PathNodeDebugState.recordProbe("WalkNodeEvaluator#getPathTypeFromState", pos,
                level.getClass().getName() + ", type=" + cir.getReturnValue());
    }
}
