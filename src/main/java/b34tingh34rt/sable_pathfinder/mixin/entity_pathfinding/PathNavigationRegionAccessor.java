package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PathNavigationRegion.class)
public interface PathNavigationRegionAccessor {
    @Accessor("level")
    Level sablePathfinder$getLevel();
}
