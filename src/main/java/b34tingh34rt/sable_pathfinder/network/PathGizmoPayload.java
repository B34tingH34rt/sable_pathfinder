package b34tingh34rt.sable_pathfinder.network;

import b34tingh34rt.sable_pathfinder.SablePathfinder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PathGizmoPayload(int entityId, int rgb, List<Segment> segments) implements CustomPacketPayload {
    public static final int MAX_SEGMENTS = 64;
    public static final Type<PathGizmoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SablePathfinder.MODID, "path_gizmo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PathGizmoPayload> STREAM_CODEC = StreamCodec.ofMember(
            PathGizmoPayload::write,
            PathGizmoPayload::read
    );

    public PathGizmoPayload {
        segments = List.copyOf(segments.size() > MAX_SEGMENTS ? segments.subList(0, MAX_SEGMENTS) : segments);
    }

    private static PathGizmoPayload read(final RegistryFriendlyByteBuf buffer) {
        final int entityId = buffer.readVarInt();
        final int rgb = buffer.readInt();
        final int segmentCount = buffer.readVarInt();
        if (segmentCount < 0 || segmentCount > MAX_SEGMENTS) {
            throw new IllegalArgumentException("Invalid path gizmo segment count: " + segmentCount);
        }

        final List<Segment> segments = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            segments.add(new Segment(readPoint(buffer), readPoint(buffer)));
        }

        return new PathGizmoPayload(entityId, rgb, segments);
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeInt(this.rgb);
        buffer.writeVarInt(this.segments.size());
        for (final Segment segment : this.segments) {
            writePoint(buffer, segment.from());
            writePoint(buffer, segment.to());
        }
    }

    @Override
    public Type<PathGizmoPayload> type() {
        return TYPE;
    }

    private static Point readPoint(final RegistryFriendlyByteBuf buffer) {
        final BlockPos worldPos = buffer.readBlockPos();
        if (!buffer.readBoolean()) {
            return Point.world(worldPos);
        }

        return new Point(worldPos, buffer.readUUID(), buffer.readBlockPos());
    }

    private static void writePoint(final RegistryFriendlyByteBuf buffer, final Point point) {
        buffer.writeBlockPos(point.worldPos());
        buffer.writeBoolean(point.usesSubLevel());
        if (point.usesSubLevel()) {
            buffer.writeUUID(point.subLevelId());
            buffer.writeBlockPos(point.localPos());
        }
    }

    public record Segment(Point from, Point to) {
    }

    public record Point(BlockPos worldPos, UUID subLevelId, BlockPos localPos) {
        public static Point world(final BlockPos worldPos) {
            return new Point(worldPos, null, null);
        }

        public boolean usesSubLevel() {
            return this.subLevelId != null && this.localPos != null;
        }
    }
}
