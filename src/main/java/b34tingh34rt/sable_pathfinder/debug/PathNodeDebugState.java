package b34tingh34rt.sable_pathfinder.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public final class PathNodeDebugState {
    private static final ThreadLocal<Map<Long, PathNodeSource>> CAPTURED_SOURCES = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Boolean> CAPTURE_ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<CaptureInfo> CAPTURE_INFO = ThreadLocal.withInitial(CaptureInfo::new);

    private PathNodeDebugState() {
    }

    public static void beginCapture() {
        CAPTURED_SOURCES.get().clear();
        CAPTURE_INFO.get().clear();
        CAPTURE_ACTIVE.set(Boolean.TRUE);
    }

    public static void record(final BlockPos pos, final PathNodeSource source) {
        if (!CAPTURE_ACTIVE.get()) {
            return;
        }

        CAPTURED_SOURCES.get().merge(pos.asLong(), source, PathNodeDebugState::preferSableSource);
        CAPTURE_INFO.get().record(pos, source);
    }

    public static void recordProbe(final String hook, final BlockPos pos, final String context) {
        if (!CAPTURE_ACTIVE.get()) {
            return;
        }

        CAPTURE_INFO.get().recordProbe(hook, pos, context);
    }

    public static void tagPath(final Path path) {
        if (path == null) {
            CAPTURE_ACTIVE.set(Boolean.FALSE);
            return;
        }

        for (int i = 0; i < path.getNodeCount(); i++) {
            final Node node = path.getNode(i);
            final BlockPos nodePos = node.asBlockPos();
            ((PathNodeMetadataAccess) node).sablePathfinder$setNodeSource(sourceForNode(nodePos));
        }

        CAPTURE_ACTIVE.set(Boolean.FALSE);
    }

    public static PathNodeSource sourceFor(final Node node) {
        return ((PathNodeMetadataAccess) node).sablePathfinder$getNodeSource();
    }

    public static String captureSummary() {
        final CaptureInfo info = CAPTURE_INFO.get();
        return "captured lookups world " + info.worldRecords +
                ", sable " + info.sableRecords +
                ", probes " + info.probeRecords +
                ", unique positions " + CAPTURED_SOURCES.get().size() +
                (info.probesByHook.isEmpty() ? "" : ", probe hooks " + formatCounts(info.probesByHook)) +
                (info.sableByLevel.isEmpty() ? "" : ", sable levels " + formatCounts(info.sableByLevel)) +
                (info.probeSamples.isEmpty() ? "" : ", probe samples " + String.join("; ", info.probeSamples)) +
                (info.sableSamples.isEmpty() ? "" : ", sable samples " + String.join("; ", info.sableSamples));
    }

    public static String describeCapturedSourcesNear(final BlockPos nodePos) {
        final Map<Long, PathNodeSource> sources = CAPTURED_SOURCES.get();
        return "direct " + formatSource(sources.get(nodePos.asLong())) +
                ", below " + formatSource(sources.get(nodePos.below().asLong())) +
                ", above " + formatSource(sources.get(nodePos.above().asLong())) +
                ", nearest sable " + formatNearbySable(nodePos, 3);
    }

    public static String describePathRegionEvidence(final Path path, final int maxNodes) {
        if (path == null) {
            return captureSummary() + " | no path returned";
        }
        if (path.getNodeCount() < 1) {
            return captureSummary() + " | path has no nodes";
        }

        final StringBuilder builder = new StringBuilder(captureSummary());
        builder.append(" | node evidence: ");

        final int shown = Math.min(path.getNodeCount(), maxNodes);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                builder.append(" || ");
            }

            final Node node = path.getNode(i);
            final BlockPos nodePos = node.asBlockPos();
            final PathNodeSource tag = sourceFor(node);
            if (i == path.getNextNodeIndex()) {
                builder.append("[next] ");
            }
            builder.append("#").append(i)
                    .append(" ")
                    .append(nodePos.toShortString())
                    .append(" tag ")
                    .append(tag == null ? "none" : tag.shortLabel())
                    .append(" | ")
                    .append(describeCapturedSourcesNear(nodePos));
        }

        final int hidden = path.getNodeCount() - shown;
        if (hidden > 0) {
            builder.append(" || +").append(hidden).append(" more");
        }

        return builder.toString();
    }

    public static String labelFor(final Node node, final int index) {
        final PathNodeSource source = sourceFor(node);
        final PathNodeSource resolvedSource = source == null ? PathNodeSource.unknownWorld(node.asBlockPos()) : source;
        final boolean showIndex = MobPathDebugState.isEnabled(MobPathDebugState.Category.NODE_LABEL_INDEX);
        final boolean showOrigin = MobPathDebugState.isEnabled(MobPathDebugState.Category.NODE_LABEL_ORIGIN);
        final boolean showLookup = MobPathDebugState.isEnabled(MobPathDebugState.Category.NODE_LABEL_LOOKUP);
        final boolean showNodePos = MobPathDebugState.isEnabled(MobPathDebugState.Category.NODE_LABEL_NODE_POS);
        final boolean showLookupPos = MobPathDebugState.isEnabled(MobPathDebugState.Category.NODE_LABEL_LOOKUP_POS);
        final boolean hasExplicitFields = showIndex || showOrigin || showLookup || showNodePos || showLookupPos;

        final StringJoiner lines = new StringJoiner("\n");
        final StringJoiner summary = new StringJoiner(" ");
        if (showIndex) {
            summary.add("#" + index);
        }
        if (showOrigin || !hasExplicitFields) {
            summary.add(resolvedSource.originLabel());
        }
        if (showLookup) {
            summary.add(resolvedSource.lookupLabel());
        }
        final String summaryText = summary.toString();
        if (!summaryText.isEmpty()) {
            lines.add(summaryText);
        }
        if (showNodePos) {
            lines.add("node " + formatBlockPos(node.x, node.y, node.z));
        }
        if (showLookupPos) {
            lines.add("lookup " + formatBlockPos(resolvedSource.lookupPos()));
        }

        return lines.toString();
    }

    public static Vec3 displayPosFor(final Node node) {
        final PathNodeSource source = sourceFor(node);
        return source == null ? node.asBlockPos().getCenter() : source.displayPos();
    }

    private static PathNodeSource sourceForNode(final BlockPos nodePos) {
        final Map<Long, PathNodeSource> sources = CAPTURED_SOURCES.get();
        PathNodeSource source = sources.get(nodePos.asLong());
        if (source != null && source.isSable()) {
            return source;
        }

        final PathNodeSource below = sources.get(nodePos.below().asLong());
        if (below != null && below.isSable()) {
            return below;
        }

        final PathNodeSource above = sources.get(nodePos.above().asLong());
        if (above != null && above.isSable()) {
            return above;
        }

        if (source != null) {
            return source;
        }
        if (below != null) {
            return below;
        }
        if (above != null) {
            return above;
        }

        return PathNodeSource.unknownWorld(nodePos);
    }

    private static PathNodeSource preferSableSource(final PathNodeSource previous, final PathNodeSource next) {
        if (previous.isSable()) {
            return previous;
        }

        return next.isSable() ? next : previous;
    }

    private static String formatBlockPos(final BlockPos pos) {
        return formatBlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static String formatBlockPos(final int x, final int y, final int z) {
        return x + "," + y + "," + z;
    }

    private static String formatSource(final PathNodeSource source) {
        return source == null ? "none" : source.shortLabel() + " via " + formatBlockPos(source.lookupPos());
    }

    private static String formatCounts(final Map<String, Integer> counts) {
        final StringJoiner joiner = new StringJoiner(", ");
        for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
            joiner.add(entry.getKey() + "=" + entry.getValue());
        }

        return joiner.toString();
    }

    private static String formatNearbySable(final BlockPos center, final int radius) {
        final Map<Long, PathNodeSource> sources = CAPTURED_SOURCES.get();
        PathNodeSource nearestSource = null;
        BlockPos nearestPos = null;
        int nearestDistance = Integer.MAX_VALUE;

        for (final Map.Entry<Long, PathNodeSource> entry : sources.entrySet()) {
            final PathNodeSource source = entry.getValue();
            if (source == null || !source.isSable()) {
                continue;
            }

            final BlockPos pos = BlockPos.of(entry.getKey());
            final int dx = Math.abs(pos.getX() - center.getX());
            final int dy = Math.abs(pos.getY() - center.getY());
            final int dz = Math.abs(pos.getZ() - center.getZ());
            if (dx > radius || dy > radius || dz > radius) {
                continue;
            }

            final int distance = dx + dy + dz;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestSource = source;
                nearestPos = pos;
            }
        }

        if (nearestSource == null || nearestPos == null) {
            return "none within " + radius;
        }

        return "d" + nearestDistance +
                " query " + nearestPos.toShortString() +
                " -> " + nearestSource.shortLabel() +
                " lookup " + nearestSource.lookupPos().toShortString();
    }

    private static final class CaptureInfo {
        private int worldRecords;
        private int sableRecords;
        private int probeRecords;
        private final Map<String, Integer> probesByHook = new LinkedHashMap<>();
        private final Map<String, Integer> sableByLevel = new LinkedHashMap<>();
        private final List<String> probeSamples = new ArrayList<>();
        private final List<String> sableSamples = new ArrayList<>();

        private void clear() {
            this.worldRecords = 0;
            this.sableRecords = 0;
            this.probeRecords = 0;
            this.probesByHook.clear();
            this.sableByLevel.clear();
            this.probeSamples.clear();
            this.sableSamples.clear();
        }

        private void record(final BlockPos pos, final PathNodeSource source) {
            if (source.isSable()) {
                this.sableRecords++;
                this.sableByLevel.merge(source.describeSubLevel(), 1, Integer::sum);
                if (this.sableSamples.size() < 8) {
                    this.sableSamples.add(pos.toShortString() + " <- " + source.shortLabel() + " lookup " + source.lookupPos().toShortString());
                }
                return;
            }

            this.worldRecords++;
        }

        private void recordProbe(final String hook, final BlockPos pos, final String context) {
            this.probeRecords++;
            this.probesByHook.merge(hook, 1, Integer::sum);
            if (this.probeSamples.size() < 12) {
                this.probeSamples.add(hook + " " + pos.toShortString() + " " + context);
            }
        }
    }
}
