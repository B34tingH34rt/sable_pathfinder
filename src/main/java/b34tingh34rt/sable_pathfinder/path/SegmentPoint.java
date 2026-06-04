package b34tingh34rt.sable_pathfinder.path;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public record SegmentPoint(BlockPos worldPos, @Nullable SubLevel subLevel, @Nullable UUID subLevelId, @Nullable BlockPos localPos) {
    public static SegmentPoint world(final BlockPos worldPos) {
        return new SegmentPoint(worldPos, null, null, null);
    }

    public static SegmentPoint subLevel(final BlockPos worldPos, final SubLevel subLevel, final BlockPos localPos) {
        return new SegmentPoint(worldPos, subLevel, subLevel.getUniqueId(), localPos);
    }

    public boolean usesSubLevel() {
        return this.subLevel != null && this.localPos != null;
    }

    public Vec3 nodeCenter() {
        if (this.usesSubLevel()) {
            return this.subLevel.logicalPose().transformPosition(this.localPos.getCenter());
        }

        return this.worldPos.getCenter();
    }

    public BlockPos projectedBlockPos() {
        return BlockPos.containing(this.nodeCenter());
    }

    public Vec3 entityPosition(final Entity entity) {
        final double offset = (double) ((int) (entity.getBbWidth() + 1.0F)) * 0.5;
        if (this.usesSubLevel()) {
            return this.subLevel.logicalPose().transformPosition(new Vec3(this.localPos.getX() + offset, this.localPos.getY(), this.localPos.getZ() + offset));
        }

        return new Vec3(this.worldPos.getX() + offset, this.worldPos.getY(), this.worldPos.getZ() + offset);
    }
}
