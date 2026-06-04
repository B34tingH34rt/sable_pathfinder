package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.path.SegmentedPathAccess;
import b34tingh34rt.sable_pathfinder.path.SegmentedPathData;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PathNavigation.class, priority = 2000)
public abstract class PathNavigationSegmentedRecomputeMixin {
    @Shadow
    @Final
    protected Level level;

    @Shadow
    protected Path path;

    @Shadow
    protected boolean hasDelayedRecomputation;

    @Inject(method = "shouldRecomputePath", at = @At("RETURN"), cancellable = true)
    private void sablePathfinder$shouldRecomputeForSegmentedSubLevelBlockChange(final BlockPos pos, final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() || this.hasDelayedRecomputation || this.path == null || this.path.isDone()) {
            return;
        }

        final SegmentedPathData segmentedPathData = ((SegmentedPathAccess) this.path).sablePathfinder$getSegmentedPathData();
        if (segmentedPathData == null || segmentedPathData.isEmpty()) {
            return;
        }

        final SubLevel changedSubLevel = Sable.HELPER.getContaining(this.level, pos);
        if (changedSubLevel == null || changedSubLevel.getUniqueId() == null) {
            return;
        }

        if (segmentedPathData.shouldRecomputeForSubLevelBlockChange(changedSubLevel.getUniqueId(), pos, this.path.getNextNodeIndex())) {
            cir.setReturnValue(true);
        }
    }
}
