package b34tingh34rt.sable_pathfinder.mixin.client;

import net.minecraft.client.renderer.debug.PathfindingRenderer;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(PathfindingRenderer.class)
public interface PathfindingRendererAccessor {
    @Accessor("pathMap")
    Map<Integer, Path> sablePathfinder$getPathMap();
}
