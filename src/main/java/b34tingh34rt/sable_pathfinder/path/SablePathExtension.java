package b34tingh34rt.sable_pathfinder.path;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public interface SablePathExtension {
    void sablePathfinder$setFrames(Level level, List<UUID> frames);

    UUID sablePathfinder$getFrame(int index);

    Vec3 sablePathfinder$getProjectedNodeCenter(Level level, int index);
}
