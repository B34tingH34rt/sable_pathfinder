package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.debug.PathNodeDebugState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NodeEvaluator.class)
public abstract class NodeEvaluatorProbeMixin {
    @Inject(method = "prepare", at = @At("HEAD"))
    private void sablePathfinder$prepare(final PathNavigationRegion region, final Mob mob, final CallbackInfo ci) {
        PathNodeDebugState.recordProbe("NodeEvaluator#prepare", mob.blockPosition(),
                "mob=" + mob.getType().builtInRegistryHolder().key().location());
    }

    @Inject(method = "getNode(III)Lnet/minecraft/world/level/pathfinder/Node;", at = @At("RETURN"))
    private void sablePathfinder$getNode(final int x, final int y, final int z, final CallbackInfoReturnable<Node> cir) {
        PathNodeDebugState.recordProbe("NodeEvaluator#getNode", new BlockPos(x, y, z), "node=" + (cir.getReturnValue() == null ? "null" : "ok"));
    }
}
