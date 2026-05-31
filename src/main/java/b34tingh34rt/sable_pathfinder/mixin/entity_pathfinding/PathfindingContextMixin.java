package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.debug.PathNodeDebugState;
import b34tingh34rt.sable_pathfinder.debug.PathNodeSource;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathfindingContext.class)
public abstract class PathfindingContextMixin {
    @Shadow
    @Final
    private CollisionGetter level;

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void sablePathfinder$getBlockState(final BlockPos blockPos, final CallbackInfoReturnable<BlockState> cir) {
        final Level worldLevel = this.sablePathfinder$getWorldLevel();
        PathNodeDebugState.recordProbe("PathfindingContext#getBlockState", blockPos,
                this.level.getClass().getName() + ", worldLevel=" + (worldLevel == null ? "none" : worldLevel.dimension().location()));
        if (worldLevel == null) {
            return;
        }

        final BlockState existing = cir.getReturnValue();
        if (!existing.isAir()) {
            PathNodeDebugState.record(blockPos, PathNodeSource.world(PathNodeSource.Lookup.BLOCK, blockPos));
            return;
        }

        final SubLevel localSubLevel = Sable.HELPER.getContaining(worldLevel, blockPos);
        final BlockState resolved = Sable.HELPER.runIncludingSubLevels(worldLevel, Vec3.atCenterOf(blockPos), false, localSubLevel,
                (candidateSubLevel, candidatePos) -> {
                    final BlockState candidateState = worldLevel.getBlockState(candidatePos);
                    if (candidateState.isAir()) {
                        return null;
                    }

                    PathNodeDebugState.record(blockPos, candidateSubLevel == null
                            ? PathNodeSource.world(PathNodeSource.Lookup.BLOCK, candidatePos)
                            : PathNodeSource.sable(PathNodeSource.Lookup.BLOCK, candidateSubLevel, candidatePos));
                    return candidateState;
                });

        if (resolved != null) {
            cir.setReturnValue(resolved);
        }
    }

    @Unique
    private Level sablePathfinder$getWorldLevel() {
        if (this.level instanceof Level worldLevel) {
            return worldLevel;
        }
        if (this.level instanceof PathNavigationRegion region) {
            return ((PathNavigationRegionAccessor) region).sablePathfinder$getLevel();
        }

        return null;
    }
}
