package b34tingh34rt.sable_pathfinder.visualization;

public final class PathVisualizationState {
    private static volatile boolean enabled = false;

    private PathVisualizationState() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(final boolean value) {
        enabled = value;
    }
}
