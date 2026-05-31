package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import net.minecraft.network.chat.Component;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PathNavigationRegion.class, priority = 2000)
public abstract class PathNavigationRegionMixin {
    @Shadow
    @Final
    protected Level level;

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void sablePathfinder$getBlockState(final BlockPos blockPos, final CallbackInfoReturnable<BlockState> cir) {
        final BlockState existing = cir.getReturnValue();

        if (!existing.isAir()) {
            return;
        }

        final SubLevel localSubLevel = Sable.HELPER.getContaining(this.level, blockPos);
        final BlockState resolved = Sable.HELPER.runIncludingSubLevels(this.level, Vec3.atCenterOf(blockPos), false, localSubLevel,
                (candidateSubLevel, candidatePos) -> {
                    final BlockState candidateState = this.level.getBlockState(candidatePos);
                    return candidateState.isAir() ? null : candidateState;
                });

        if (resolved != null) {
            cir.setReturnValue(resolved);
        }
    }

    @Inject(method = "getFluidState", at = @At("RETURN"), cancellable = true)
    private void sablePathfinder$getFluidState(final BlockPos blockPos, final CallbackInfoReturnable<FluidState> cir) {
        final FluidState existing = cir.getReturnValue();

        if (!existing.isEmpty()) {
            return;
        }

        final SubLevel localSubLevel = Sable.HELPER.getContaining(this.level, blockPos);
        final FluidState resolved = Sable.HELPER.runIncludingSubLevels(this.level, Vec3.atCenterOf(blockPos), false, localSubLevel,
                (candidateSubLevel, candidatePos) -> {
                    final FluidState candidateState = this.level.getFluidState(candidatePos);
                    return candidateState.isEmpty() ? null : candidateState;
                });

        if (resolved != null) {
            cir.setReturnValue(resolved);
        }
    }
}