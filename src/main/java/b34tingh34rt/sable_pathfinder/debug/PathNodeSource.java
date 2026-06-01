package b34tingh34rt.sable_pathfinder.debug;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public record PathNodeSource(Source source, Lookup lookup, String subLevelName, String subLevelId, SubLevel subLevel,
                             BlockPos queryPos, BlockPos lookupPos) {
    public static PathNodeSource world(final Lookup lookup, final BlockPos lookupPos) {
        return new PathNodeSource(Source.WORLD, lookup, null, null, null, lookupPos.immutable(), lookupPos.immutable());
    }

    public static PathNodeSource sable(final Lookup lookup, final SubLevel subLevel, final BlockPos lookupPos) {
        final String id = subLevel.getUniqueId() == null ? "no-id" : subLevel.getUniqueId().toString().substring(0, 8);
        return new PathNodeSource(Source.SABLE, lookup, subLevel.getName(), id, subLevel, lookupPos.immutable(), lookupPos.immutable());
    }

    public static PathNodeSource unknownWorld(final BlockPos nodePos) {
        return new PathNodeSource(Source.WORLD, Lookup.UNKNOWN, null, null, null, nodePos.immutable(), nodePos.immutable());
    }

    public PathNodeSource withQueryPos(final BlockPos queryPos) {
        return new PathNodeSource(this.source, this.lookup, this.subLevelName, this.subLevelId, this.subLevel,
                queryPos.immutable(), this.lookupPos);
    }

    public Vec3 projectedNodeEntityPos(final BlockPos nodePos, final Entity entity) {
        final double horizontalOffset = (double) ((int) (entity.getBbWidth() + 1.0F)) * 0.5;
        return this.projectNodePos(nodePos, horizontalOffset, horizontalOffset);
    }

    public Vec3 projectedNodeCenter(final BlockPos nodePos) {
        return this.projectNodePos(nodePos, 0.5, 0.5);
    }

    public BlockPos projectedNodeBlock(final BlockPos nodePos) {
        return BlockPos.containing(this.projectedNodeCenter(nodePos));
    }

    private Vec3 projectNodePos(final BlockPos nodePos, final double xOffset, final double zOffset) {
        final int dx = nodePos.getX() - this.queryPos.getX();
        final int dy = nodePos.getY() - this.queryPos.getY();
        final int dz = nodePos.getZ() - this.queryPos.getZ();
        final Vec3 localPos = new Vec3(
                this.lookupPos.getX() + dx + xOffset,
                this.lookupPos.getY() + dy,
                this.lookupPos.getZ() + dz + zOffset
        );

        return this.subLevel == null ? localPos : this.subLevel.logicalPose().transformPosition(localPos);
    }

    public String shortLabel() {
        if (this.source == Source.WORLD) {
            return this.lookup == Lookup.UNKNOWN ? "world" : "world/" + this.lookup.id;
        }

        return "sable/" + this.lookup.id + "/" + this.describeSubLevel();
    }

    public String describeSubLevel() {
        if (this.source == Source.WORLD) {
            return "none";
        }

        return (this.subLevelName == null ? "unnamed" : this.subLevelName) + "/" + this.subLevelId;
    }

    public String originLabel() {
        if (this.source == Source.WORLD) {
            return "world";
        }

        return "sable " + this.describeSubLevel();
    }

    public String lookupLabel() {
        return this.lookup.id;
    }

    public boolean isSable() {
        return this.source == Source.SABLE;
    }

    public enum Source {
        WORLD,
        SABLE
    }

    public enum Lookup {
        BLOCK("block"),
        FLUID("fluid"),
        UNKNOWN("unknown");

        private final String id;

        Lookup(final String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }
    }
}
