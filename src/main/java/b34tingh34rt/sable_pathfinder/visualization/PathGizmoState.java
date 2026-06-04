package b34tingh34rt.sable_pathfinder.visualization;

import b34tingh34rt.sable_pathfinder.SablePathfinder;
import b34tingh34rt.sable_pathfinder.network.PathGizmoPayload;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PathGizmoState {
    private static final long TIMEOUT_MILLIS = 1500L;
    private static final ConcurrentMap<Integer, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static volatile boolean loggedFirstPayload = false;

    private PathGizmoState() {
    }

    public static void accept(final PathGizmoPayload payload) {
        if (payload.segments().isEmpty()) {
            ENTRIES.remove(payload.entityId());
            return;
        }

        ENTRIES.put(payload.entityId(), new Entry(payload.entityId(), payload.rgb(), payload.segments(), Util.getMillis()));
        if (!loggedFirstPayload) {
            loggedFirstPayload = true;
            SablePathfinder.LOGGER.info("Received path gizmo payload for entity {} with {} segments.", payload.entityId(), payload.segments().size());
        }
    }

    public static Collection<Entry> entries() {
        final long now = Util.getMillis();
        ENTRIES.entrySet().removeIf(entry -> now - entry.getValue().updatedAtMillis() > TIMEOUT_MILLIS);
        return List.copyOf(ENTRIES.values());
    }

    public record Entry(int entityId, int rgb, List<PathGizmoPayload.Segment> segments, long updatedAtMillis) {
    }
}
