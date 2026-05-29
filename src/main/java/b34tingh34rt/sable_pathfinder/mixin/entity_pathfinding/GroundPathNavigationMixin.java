package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GroundPathNavigation.class, priority = 2000)
public abstract class GroundPathNavigationMixin {
    @Shadow
    public abstract Path createPath(BlockPos blockPos, int i);

    @Inject(method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void sablePathfinder$createPath(final BlockPos pos, final int accuracy, final CallbackInfoReturnable<Path> cir) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(((PathNavigationAccessor) this).sablePathfinder$getMob());
        final SubLevel containingSubLevel = Sable.HELPER.getContaining(((PathNavigationAccessor) this).sablePathfinder$getMob());
        final SubLevel targetSubLevel = Sable.HELPER.getContaining(((PathNavigationAccessor) this).sablePathfinder$getMob().level(), pos);

        if (trackingSubLevel != null || containingSubLevel != null || targetSubLevel != null) {
            cir.setReturnValue(((PathNavigationAccessor) this).sablePathfinder$createPath(ImmutableSet.of(pos), 8, false, accuracy));
        }
    }

    @Inject(method = "createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void sablePathfinder$createPath(final Entity entity, final int i, final CallbackInfoReturnable<Path> cir) {
        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(((PathNavigationAccessor) this).sablePathfinder$getMob());
        if (trackingSubLevel != null) {
            cir.setReturnValue(((PathNavigationAccessor) this).sablePathfinder$createPath(ImmutableSet.of(entity.blockPosition()), 16, true, i));
        }
    }
}