package b34tingh34rt.sable_pathfinder.client;

import b34tingh34rt.sable_pathfinder.SablePathfinder;
import b34tingh34rt.sable_pathfinder.debug.PathDebugMarkers;
import b34tingh34rt.sable_pathfinder.mixin.client.PathfindingRendererAccessor;
import b34tingh34rt.sable_pathfinder.visualization.PathVisualizationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Locale;
import java.util.Map;

@EventBusSubscriber(modid = SablePathfinder.MODID, value = Dist.CLIENT)
public final class PathVisualizerRenderer {
    private static final int SABLE_PATHFINDER$MAX_SURROUNDING_OVERLAY_BOXES = 1200;
    private static final int SABLE_PATHFINDER$MAX_SURROUNDING_LABELS = 220;

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

        final Map<Integer, Path> pathMap = ((PathfindingRendererAccessor) minecraft.debugRenderer.pathfindingRenderer).sablePathfinder$getPathMap();
        if (pathMap.isEmpty()) {
            return;
        }

        final var bufferSource = minecraft.renderBuffers().bufferSource();
        final double cameraX = event.getCamera().getPosition().x;
        final double cameraY = event.getCamera().getPosition().y;
        final double cameraZ = event.getCamera().getPosition().z;

        minecraft.debugRenderer.pathfindingRenderer.render(
                event.getPoseStack(),
                bufferSource,
                cameraX,
                cameraY,
                cameraZ
        );

        try {
            int renderedBoxes = 0;
            int renderedLabels = 0;
            for (final Path path : pathMap.values()) {
                if (path == null) {
                    continue;
                }

                final Path.DebugData debugData = path.debugData();
                if (debugData == null) {
                    continue;
                }

                for (final Node node : debugData.openSet()) {
                    if (renderedBoxes >= SABLE_PATHFINDER$MAX_SURROUNDING_OVERLAY_BOXES
                            && renderedLabels >= SABLE_PATHFINDER$MAX_SURROUNDING_LABELS) {
                        break;
                    }

                    if (sablePathfinder$renderSurroundingNode(event, minecraft, bufferSource, node, cameraX, cameraY, cameraZ, false)) {
                        renderedBoxes++;
                        if (renderedLabels < SABLE_PATHFINDER$MAX_SURROUNDING_LABELS) {
                            sablePathfinder$renderSurroundingNodeLabel(event, bufferSource, node, cameraX, cameraY, cameraZ, false);
                            renderedLabels++;
                        }
                    }
                }

                for (final Node node : debugData.closedSet()) {
                    if (renderedBoxes >= SABLE_PATHFINDER$MAX_SURROUNDING_OVERLAY_BOXES
                            && renderedLabels >= SABLE_PATHFINDER$MAX_SURROUNDING_LABELS) {
                        break;
                    }

                    if (sablePathfinder$renderSurroundingNode(event, minecraft, bufferSource, node, cameraX, cameraY, cameraZ, true)) {
                        renderedBoxes++;
                        if (renderedLabels < SABLE_PATHFINDER$MAX_SURROUNDING_LABELS) {
                            sablePathfinder$renderSurroundingNodeLabel(event, bufferSource, node, cameraX, cameraY, cameraZ, true);
                            renderedLabels++;
                        }
                    }
                }

                if (renderedBoxes >= SABLE_PATHFINDER$MAX_SURROUNDING_OVERLAY_BOXES
                        && renderedLabels >= SABLE_PATHFINDER$MAX_SURROUNDING_LABELS) {
                    break;
                }
            }
        } catch (final Exception exception) {
            sablePathfinder$overlayEnabled = false;
            if (!sablePathfinder$overlayErrorLogged) {
                SablePathfinder.LOGGER.warn("Path visualizer overlay failed and has been disabled for this run.", exception);
                sablePathfinder$overlayErrorLogged = true;
            }
        }
    }

    private static boolean sablePathfinder$renderSurroundingNode(
            final RenderLevelStageEvent event,
            final Minecraft minecraft,
            final net.minecraft.client.renderer.MultiBufferSource bufferSource,
            final Node node,
            final double cameraX,
            final double cameraY,
            final double cameraZ,
            final boolean fromClosedSet
    ) {
        if (node == null || node.walkedDistance != PathDebugMarkers.SURROUNDING_NODE_MARKER) {
            return false;
        }

        final BlockPos blockPos = node.asBlockPos();
        if (!minecraft.level.isLoaded(blockPos)) {
            return false;
        }

        final float[] color = sablePathfinder$colorForType(node.type, fromClosedSet);

        final double x1 = blockPos.getX() - cameraX;
        final double y1 = blockPos.getY() - cameraY;
        final double z1 = blockPos.getZ() - cameraZ;
        final double x2 = x1 + 1.0;
        final double y2 = y1 + 1.0;
        final double z2 = z1 + 1.0;

        DebugRenderer.renderFilledBox(event.getPoseStack(), bufferSource, x1, y1, z1, x2, y2, z2, color[0], color[1], color[2], color[3]);
        return true;
    }

    private static void sablePathfinder$renderSurroundingNodeLabel(
            final RenderLevelStageEvent event,
            final net.minecraft.client.renderer.MultiBufferSource bufferSource,
            final Node node,
            final double cameraX,
            final double cameraY,
            final double cameraZ,
            final boolean fromClosedSet
    ) {
        final double x = node.x + 0.5;
        final double y = node.y + 0.15;
        final double z = node.z + 0.5;
        final double dx = x - cameraX;
        final double dy = y - cameraY;
        final double dz = z - cameraZ;
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 80.0D) {
            return;
        }

        final String prefix = fromClosedSet ? "C" : "O";
        final String label = "SPF " + prefix + " " + node.type + " " + String.format(Locale.ROOT, "%.2f", node.costMalus);
        final int color = fromClosedSet ? 0xFFB46E6E : 0xFF7EE8FF;
        DebugRenderer.renderFloatingText(event.getPoseStack(), bufferSource, label, x, y, z, color, 0.012F, true, 0.0F, true);
    }

    private static float[] sablePathfinder$colorForType(final PathType type, final boolean fromClosedSet) {
        if (fromClosedSet || type == PathType.BLOCKED || type == PathType.DAMAGE_FIRE || type == PathType.DAMAGE_OTHER || type == PathType.LAVA) {
            return new float[]{1.0F, 0.35F, 0.35F, 0.26F};
        }

        if (type == PathType.WATER || type == PathType.WATER_BORDER) {
            return new float[]{0.35F, 0.62F, 1.0F, 0.24F};
        }

        if (type == PathType.WALKABLE || type == PathType.WALKABLE_DOOR || type == PathType.OPEN || type == PathType.DOOR_OPEN) {
            return new float[]{0.35F, 1.0F, 0.45F, 0.22F};
        }

        return new float[]{0.75F, 0.75F, 1.0F, 0.22F};
    }
}