package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import b34tingh34rt.sable_pathfinder.debug.PathNodeMetadataAccess;
import b34tingh34rt.sable_pathfinder.debug.PathNodeSource;
import net.minecraft.world.level.pathfinder.Node;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Node.class)
public abstract class PathNodeMetadataMixin implements PathNodeMetadataAccess {
    @Unique
    private PathNodeSource sablePathfinder$nodeSource;

    @Override
    public PathNodeSource sablePathfinder$getNodeSource() {
        return this.sablePathfinder$nodeSource;
    }

    @Override
    public void sablePathfinder$setNodeSource(final PathNodeSource source) {
        this.sablePathfinder$nodeSource = source;
    }

    @Inject(method = "cloneAndMove", at = @At("RETURN"))
    private void sablePathfinder$copyNodeSource(final int x, final int y, final int z, final CallbackInfoReturnable<Node> cir) {
        ((PathNodeMetadataAccess) cir.getReturnValue()).sablePathfinder$setNodeSource(this.sablePathfinder$nodeSource);
    }
}
