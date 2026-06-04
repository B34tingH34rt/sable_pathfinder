package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.path.SegmentedPathAccess;
import b34tingh34rt.sable_pathfinder.path.SegmentedPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(value = Path.class, priority = 2100)
public abstract class PathSegmentedMixin implements SegmentedPathAccess {
    @Unique
    @Nullable
    private SegmentedPathData sablePathfinder$segmentedPathData;

    @Override
    public SegmentedPathData sablePathfinder$getSegmentedPathData() {
        return this.sablePathfinder$segmentedPathData;
    }

    @Override
    public void sablePathfinder$setSegmentedPathData(final SegmentedPathData data) {
        this.sablePathfinder$segmentedPathData = data;
    }

    @Inject(method = "getEntityPosAtNode", at = @At("RETURN"), cancellable = true)
    private void sablePathfinder$getSegmentedEntityPosAtNode(final Entity entity, final int index, final CallbackInfoReturnable<Vec3> cir) {
        if (this.sablePathfinder$segmentedPathData != null && !this.sablePathfinder$segmentedPathData.isEmpty()) {
            cir.setReturnValue(this.sablePathfinder$segmentedPathData.getEntityPosAtNode(entity, index));
        }
    }

    @Inject(method = "getNodePos", at = @At("RETURN"), cancellable = true)
    private void sablePathfinder$getSegmentedNodePos(final int index, final CallbackInfoReturnable<BlockPos> cir) {
        if (this.sablePathfinder$segmentedPathData != null && !this.sablePathfinder$segmentedPathData.isEmpty()) {
            cir.setReturnValue(this.sablePathfinder$segmentedPathData.getNodePos(index));
        }
    }

    @Inject(method = "getNextNodePos", at = @At("RETURN"), cancellable = true)
    private void sablePathfinder$getSegmentedNextNodePos(final CallbackInfoReturnable<BlockPos> cir) {
        final Path path = (Path) (Object) this;
        if (this.sablePathfinder$segmentedPathData != null && !this.sablePathfinder$segmentedPathData.isEmpty() && !path.isDone()) {
            cir.setReturnValue(this.sablePathfinder$segmentedPathData.getNodePos(path.getNextNodeIndex()));
        }
    }

    @Inject(method = "copy", at = @At("RETURN"))
    private void sablePathfinder$copySegmentedPathData(final CallbackInfoReturnable<Path> cir) {
        if (this.sablePathfinder$segmentedPathData != null) {
            ((SegmentedPathAccess) cir.getReturnValue()).sablePathfinder$setSegmentedPathData(this.sablePathfinder$segmentedPathData);
        }
    }

    @Inject(method = "truncateNodes", at = @At("TAIL"))
    private void sablePathfinder$truncateSegmentedPathData(final int length, final CallbackInfo ci) {
        if (this.sablePathfinder$segmentedPathData != null) {
            this.sablePathfinder$segmentedPathData = this.sablePathfinder$segmentedPathData.truncate(length);
        }
    }

    @Inject(method = "replaceNode", at = @At("TAIL"))
    private void sablePathfinder$clearSegmentedPathDataOnNodeReplace(final int index, final Node point, final CallbackInfo ci) {
        this.sablePathfinder$segmentedPathData = null;
    }
}
