package b34tingh34rt.sable_pathfinder.client;

import b34tingh34rt.sable_pathfinder.SablePathfinder;
import b34tingh34rt.sable_pathfinder.visualization.PathGizmoState;
import b34tingh34rt.sable_pathfinder.visualization.PathVisualizationState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

import java.util.Collection;

@EventBusSubscriber(modid = SablePathfinder.MODID, value = Dist.CLIENT)
public final class PathVisualizerRenderer {
    private static final int SABLE_PATHFINDER$MAX_GIZMO_NODE_MARKERS = 1800;
    private static final double SABLE_PATHFINDER$NODE_MARKER_SIZE = 0.1;
    private static final double SABLE_PATHFINDER$LINE_WIDTH = 2.0;

    private static boolean sablePathfinder$overlayEnabled = true;
    private static boolean sablePathfinder$overlayErrorLogged = false;

    private PathVisualizerRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        if (!sablePathfinder$overlayEnabled || !PathVisualizationState.isEnabled() || minecraft.level == null || minecraft.player == null) {
            return;
        }

        final Collection<PathGizmoState.Entry> gizmoEntries = PathGizmoState.entries();
        if (gizmoEntries.isEmpty()) {
            return;
        }

        final MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        try {
            sablePathfinder$renderPathGizmos(event, bufferSource, gizmoEntries);
        } catch (final Exception exception) {
            sablePathfinder$overlayEnabled = false;
            if (!sablePathfinder$overlayErrorLogged) {
                SablePathfinder.LOGGER.warn("Path visualizer overlay failed and has been disabled for this run.", exception);
                sablePathfinder$overlayErrorLogged = true;
            }
        } finally {
            bufferSource.endBatch(RenderType.debugFilledBox());
            bufferSource.endBatch(RenderType.debugLineStrip(SABLE_PATHFINDER$LINE_WIDTH));
            bufferSource.endBatch();
        }
    }

    private static void sablePathfinder$renderPathGizmos(
            final RenderLevelStageEvent event,
            final MultiBufferSource bufferSource,
            final Collection<PathGizmoState.Entry> gizmoEntries
    ) {
        int renderedMarkers = 0;
        for (final PathGizmoState.Entry entry : gizmoEntries) {
            if (entry.nodes().isEmpty()) {
                continue;
            }

            final int red = (entry.rgb() >> 16) & 0xFF;
            final int green = (entry.rgb() >> 8) & 0xFF;
            final int blue = entry.rgb() & 0xFF;
            final float redFloat = red / 255.0F;
            final float greenFloat = green / 255.0F;
            final float blueFloat = blue / 255.0F;

            sablePathfinder$renderPathLines(event, bufferSource, entry.nodes(), red, green, blue);

            for (int i = 0; i < entry.nodes().size(); i++) {
                if (renderedMarkers >= SABLE_PATHFINDER$MAX_GIZMO_NODE_MARKERS) {
                    return;
                }

                final BlockPos node = entry.nodes().get(i);
                final float alpha = i == 0 ? 0.85F : 0.55F;
                sablePathfinder$renderNodeMarker(event, bufferSource, node, redFloat, greenFloat, blueFloat, alpha);
                renderedMarkers++;
            }
        }
    }

    private static void sablePathfinder$renderPathLines(
            final RenderLevelStageEvent event,
            final MultiBufferSource bufferSource,
            final java.util.List<BlockPos> nodes,
            final int red,
            final int green,
            final int blue
    ) {
        if (nodes.size() < 2) {
            return;
        }

        final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }

        final Vec3 cameraPosition = camera.getPosition();
        final VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.debugLineStrip(SABLE_PATHFINDER$LINE_WIDTH));
        for (final BlockPos node : nodes) {
            final Vec3 point = sablePathfinder$projectNodeCenter(node, cameraPosition);
            lineConsumer.addVertex(event.getPoseStack().last(), (float) point.x, (float) point.y, (float) point.z).setColor(red, green, blue, 220);
        }

        if (bufferSource instanceof MultiBufferSource.BufferSource immediate) {
            immediate.endBatch(RenderType.debugLineStrip(SABLE_PATHFINDER$LINE_WIDTH));
        }
    }

    private static void sablePathfinder$renderNodeMarker(
            final RenderLevelStageEvent event,
            final MultiBufferSource bufferSource,
            final BlockPos node,
            final float red,
            final float green,
            final float blue,
            final float alpha
    ) {
        final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }

        final double halfSize = SABLE_PATHFINDER$NODE_MARKER_SIZE * 0.5;
        final AABB markerBox = new AABB(-halfSize, -halfSize, -halfSize, halfSize, halfSize, halfSize);
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(node);

        if (subLevel == null) {
            final Vec3 center = node.getCenter().subtract(camera.getPosition());
            DebugRenderer.renderFilledBox(event.getPoseStack(), bufferSource, markerBox.move(center), red, green, blue, alpha);
            return;
        }

        final var pose = subLevel.renderPose();
        final Vec3 center = pose.transformPosition(node.getCenter()).subtract(camera.getPosition());
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(center.x, center.y, center.z);
        event.getPoseStack().mulPose(new Quaternionf(pose.orientation()));
        DebugRenderer.renderFilledBox(event.getPoseStack(), bufferSource, markerBox, red, green, blue, alpha);
        event.getPoseStack().popPose();
    }

    private static Vec3 sablePathfinder$projectNodeCenter(final BlockPos node, final Vec3 cameraPosition) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(node);
        if (subLevel == null) {
            return node.getCenter().subtract(cameraPosition);
        }

        return subLevel.renderPose().transformPosition(node.getCenter()).subtract(cameraPosition);
    }
}
