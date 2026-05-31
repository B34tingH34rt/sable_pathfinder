package b34tingh34rt.sable_pathfinder.debug;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public record PathNodeSource(Source source, Lookup lookup, String subLevelName, String subLevelId, BlockPos lookupPos, Vec3 displayPos) {
    public static PathNodeSource world(final Lookup lookup, final BlockPos lookupPos) {
        return new PathNodeSource(Source.WORLD, lookup, null, null, lookupPos.immutable(), lookupPos.getCenter());
    }

    public static PathNodeSource sable(final Lookup lookup, final SubLevel subLevel, final BlockPos lookupPos) {
        final String id = subLevel.getUniqueId() == null ? "no-id" : subLevel.getUniqueId().toString().substring(0, 8);
        return new PathNodeSource(Source.SABLE, lookup, subLevel.getName(), id, lookupPos.immutable(), subLevel.logicalPose().transformPosition(lookupPos.getCenter()));
    }

    public static PathNodeSource unknownWorld(final BlockPos nodePos) {
        return new PathNodeSource(Source.WORLD, Lookup.UNKNOWN, null, null, nodePos.immutable(), nodePos.getCenter());
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
