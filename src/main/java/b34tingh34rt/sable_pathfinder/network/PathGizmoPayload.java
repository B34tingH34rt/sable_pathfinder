package b34tingh34rt.sable_pathfinder.network;

import b34tingh34rt.sable_pathfinder.SablePathfinder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record PathGizmoPayload(int entityId, int rgb, List<BlockPos> nodes) implements CustomPacketPayload {
    public static final int MAX_NODES = 64;
    public static final Type<PathGizmoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SablePathfinder.MODID, "path_gizmo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PathGizmoPayload> STREAM_CODEC = StreamCodec.ofMember(
            PathGizmoPayload::write,
            PathGizmoPayload::read
    );

    public PathGizmoPayload {
        nodes = List.copyOf(nodes.size() > MAX_NODES ? nodes.subList(0, MAX_NODES) : nodes);
    }

    private static PathGizmoPayload read(final RegistryFriendlyByteBuf buffer) {
        final int entityId = buffer.readVarInt();
        final int rgb = buffer.readInt();
        final int nodeCount = buffer.readVarInt();
        if (nodeCount < 0 || nodeCount > MAX_NODES) {
            throw new IllegalArgumentException("Invalid path gizmo node count: " + nodeCount);
        }

        final List<BlockPos> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            nodes.add(buffer.readBlockPos());
        }

        return new PathGizmoPayload(entityId, rgb, nodes);
    }

    private void write(final RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeInt(this.rgb);
        buffer.writeVarInt(this.nodes.size());
        for (final BlockPos node : this.nodes) {
            buffer.writeBlockPos(node);
        }
    }

    @Override
    public Type<PathGizmoPayload> type() {
        return TYPE;
    }
}
