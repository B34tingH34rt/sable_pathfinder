package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Exposes internal fields of vanilla Path so that CompositePath can sync
 * its superclass state when switching segments.
 *
 * nodes and nextNodeIndex are package-private in vanilla, so a direct
 * subclass outside net.minecraft.world.level.pathfinder cannot write to
 * them without this accessor.
 */
@Mixin(Path.class)
public interface PathAccessor {

    @Accessor("nodes")
    List<Node> sablePathfinder$getNodes();

    @Accessor("nodes")
    void sablePathfinder$setNodes(List<Node> nodes);

    @Accessor("nextNodeIndex")
    int sablePathfinder$getNextNodeIndex();

    @Accessor("nextNodeIndex")
    void sablePathfinder$setNextNodeIndex(int index);
}
