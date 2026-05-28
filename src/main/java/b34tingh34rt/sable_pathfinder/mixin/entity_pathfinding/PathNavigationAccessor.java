package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

@Mixin(PathNavigation.class)
public interface PathNavigationAccessor {
    @Accessor("mob")
    Mob sablePathfinder$getMob();

    @Invoker("createPath")
    Path sablePathfinder$createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy);
}
