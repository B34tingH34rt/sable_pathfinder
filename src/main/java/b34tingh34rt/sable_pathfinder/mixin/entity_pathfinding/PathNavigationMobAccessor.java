package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PathNavigation.class)
public interface PathNavigationMobAccessor {
    @Accessor("mob")
    Mob sablePathfinder$getMob();
}
