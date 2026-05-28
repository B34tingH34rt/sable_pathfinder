package b34tingh34rt.sable_pathfinder.debug;

public final class PathfinderDebugState {
    private static volatile String lastFailure = "No Sable pathing attempt recorded.";

    private PathfinderDebugState() {
    }

    public static void setLastFailure(final String failure) {
        lastFailure = failure;
    }

    public static String getLastFailure() {
        return lastFailure;
    }
}
