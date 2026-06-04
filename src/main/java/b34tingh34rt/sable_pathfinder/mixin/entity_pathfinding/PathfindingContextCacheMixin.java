package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(value = PathfindingContext.class, priority = 2000)
public abstract class PathfindingContextCacheMixin {
    @Shadow
    @Final
    @Mutable
    @Nullable
    private PathTypeCache cache;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sablePathfinder$disablePathTypeCacheForNavigationRegion(final CollisionGetter level, final Mob mob, final CallbackInfo ci) {
        if (level instanceof PathNavigationRegion) {
            this.cache = null;
        }
    }
}
