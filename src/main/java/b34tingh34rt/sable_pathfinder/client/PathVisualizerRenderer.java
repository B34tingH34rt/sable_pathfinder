package b34tingh34rt.sable_pathfinder.client;

import b34tingh34rt.sable_pathfinder.SablePathfinder;
import b34tingh34rt.sable_pathfinder.network.PathGizmoPayload;
import b34tingh34rt.sable_pathfinder.visualization.PathGizmoState;
import b34tingh34rt.sable_pathfinder.visualization.PathVisualizationState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
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
import java.util.UUID;

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
            if (entry.segments().isEmpty()) {
                continue;
            }

            final int red = (entry.rgb() >> 16) & 0xFF;
            final int green = (entry.rgb() >> 8) & 0xFF;
            final int blue = entry.rgb() & 0xFF;
            final float redFloat = red / 255.0F;
            final float greenFloat = green / 255.0F;
            final float blueFloat = blue / 255.0F;

            sablePathfinder$renderPathLines(event, bufferSource, entry.segments(), red, green, blue);

            for (int i = 0; i < entry.segments().size(); i++) {
                if (renderedMarkers >= SABLE_PATHFINDER$MAX_GIZMO_NODE_MARKERS) {
                    return;
                }

                final PathGizmoPayload.Segment segment = entry.segments().get(i);
                final float alpha = i == 0 ? 0.85F : 0.55F;
                if (i == 0) {
                    sablePathfinder$renderNodeMarker(event, bufferSource, segment.from(), redFloat, greenFloat, blueFloat, alpha);
                    renderedMarkers++;
                }
                if (renderedMarkers >= SABLE_PATHFINDER$MAX_GIZMO_NODE_MARKERS) {
                    return;
                }
                sablePathfinder$renderNodeMarker(event, bufferSource, segment.to(), redFloat, greenFloat, blueFloat, 0.55F);
                renderedMarkers++;
            }
        }
    }

    private static void sablePathfinder$renderPathLines(
            final RenderLevelStageEvent event,
            final MultiBufferSource bufferSource,
            final java.util.List<PathGizmoPayload.Segment> segments,
            final int red,
            final int green,
            final int blue
    ) {
        if (segments.isEmpty()) {
            return;
        }

        final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }

        final Vec3 cameraPosition = camera.getPosition();
        for (final PathGizmoPayload.Segment segment : segments) {
            final VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.debugLineStrip(SABLE_PATHFINDER$LINE_WIDTH));
            final Vec3 from = sablePathfinder$resolvePoint(segment.from(), cameraPosition).center();
            final Vec3 to = sablePathfinder$resolvePoint(segment.to(), cameraPosition).center();
            lineConsumer.addVertex(event.getPoseStack().last(), (float) from.x, (float) from.y, (float) from.z).setColor(red, green, blue, 220);
            lineConsumer.addVertex(event.getPoseStack().last(), (float) to.x, (float) to.y, (float) to.z).setColor(red, green, blue, 220);
            if (bufferSource instanceof MultiBufferSource.BufferSource immediate) {
                immediate.endBatch(RenderType.debugLineStrip(SABLE_PATHFINDER$LINE_WIDTH));
            }
        }
    }

    private static void sablePathfinder$renderNodeMarker(
            final RenderLevelStageEvent event,
            final MultiBufferSource bufferSource,
            final PathGizmoPayload.Point point,
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
        final ResolvedPoint resolvedPoint = sablePathfinder$resolvePoint(point, camera.getPosition());

        if (resolvedPoint.subLevel() == null) {
            DebugRenderer.renderFilledBox(event.getPoseStack(), bufferSource, markerBox.move(resolvedPoint.center()), red, green, blue, alpha);
            return;
        }

        final var pose = resolvedPoint.subLevel().renderPose();
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(resolvedPoint.center().x, resolvedPoint.center().y, resolvedPoint.center().z);
        event.getPoseStack().mulPose(new Quaternionf(pose.orientation()));
        DebugRenderer.renderFilledBox(event.getPoseStack(), bufferSource, markerBox, red, green, blue, alpha);
        event.getPoseStack().popPose();
    }

    private static ResolvedPoint sablePathfinder$resolvePoint(final PathGizmoPayload.Point point, final Vec3 cameraPosition) {
        final ClientSubLevel subLevel = point.usesSubLevel() ? sablePathfinder$getClientSubLevel(point.subLevelId()) : null;
        if (subLevel == null || point.localPos() == null) {
            return new ResolvedPoint(point.worldPos().getCenter().subtract(cameraPosition), null);
        }

        return new ResolvedPoint(subLevel.renderPose().transformPosition(point.localPos().getCenter()).subtract(cameraPosition), subLevel);
    }

    private static ClientSubLevel sablePathfinder$getClientSubLevel(final UUID subLevelId) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }

        final SubLevel subLevel = SubLevelContainer.getContainer(minecraft.level).getSubLevel(subLevelId);
        return subLevel instanceof ClientSubLevel clientSubLevel ? clientSubLevel : null;
    }

    private record ResolvedPoint(Vec3 center, ClientSubLevel subLevel) {
    }
}
