package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.path.SablePathExtension;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(value = Path.class, priority = 2100)
public abstract class PathMixin implements SablePathExtension {
    @Unique
    private Level sablePathfinder$level;

    @Unique
    private List<UUID> sablePathfinder$frames = List.of();

    @Inject(method = "getEntityPosAtNode", at = @At("RETURN"), cancellable = true)
    private void sablePathfinder$getEntityPosAtNode(final Entity entity, final int index, final CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(this.sablePathfinder$getProjected(cir.getReturnValue(), entity.level(), index));
    }

    @Inject(method = "getNodePos", at = @At("RETURN"), cancellable = true)
    private void sablePathfinder$getNodePos(final int index, final CallbackInfoReturnable<BlockPos> cir) {
        final Vec3 projected = this.sablePathfinder$getProjected(Vec3.atCenterOf(cir.getReturnValue()), this.sablePathfinder$level, index);
        cir.setReturnValue(BlockPos.containing(projected));
    }

    @Inject(method = "getNextNodePos", at = @At("RETURN"), cancellable = true)
    private void sablePathfinder$getNextNodePos(final CallbackInfoReturnable<BlockPos> cir) {
        final Path self = (Path) (Object) this;
        final Vec3 projected = this.sablePathfinder$getProjected(Vec3.atCenterOf(cir.getReturnValue()), this.sablePathfinder$level, self.getNextNodeIndex());
        cir.setReturnValue(BlockPos.containing(projected));
    }

    @Inject(method = "copy", at = @At("RETURN"))
    private void sablePathfinder$copy(final CallbackInfoReturnable<Path> cir) {
        ((SablePathExtension) cir.getReturnValue()).sablePathfinder$setFrames(this.sablePathfinder$level, this.sablePathfinder$frames);
    }

    @Override
    public void sablePathfinder$setFrames(final Level level, final List<UUID> frames) {
        this.sablePathfinder$level = level;
        this.sablePathfinder$frames = new ArrayList<>(frames);
    }

    @Override
    public UUID sablePathfinder$getFrame(final int index) {
        if (index < 0 || index >= this.sablePathfinder$frames.size()) {
            return null;
        }
        return this.sablePathfinder$frames.get(index);
    }

    @Override
    public Vec3 sablePathfinder$getProjectedNodeCenter(final Level level, final int index) {
        final Path self = (Path) (Object) this;
        return this.sablePathfinder$getProjected(Vec3.atCenterOf(self.getNode(index).asBlockPos()), level, index);
    }

    @Unique
    private Vec3 sablePathfinder$getProjected(final Vec3 fallback, final Level explicitLevel, final int index) {
        final UUID frame = this.sablePathfinder$getFrame(index);
        if (frame == null) {
            return fallback;
        }

        final Level level = explicitLevel != null ? explicitLevel : this.sablePathfinder$level;
        if (level == null) {
            return fallback;
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return fallback;
        }

        final SubLevel subLevel = container.getSubLevel(frame);
        if (subLevel == null || subLevel.isRemoved()) {
            return fallback;
        }

        return subLevel.logicalPose().transformPosition(fallback);
    }
}
