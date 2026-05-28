package b34tingh34rt.sable_pathfinder.debug;

public final class MobPathDebugState {
    private static boolean enabled;

    private MobPathDebugState() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(final boolean value) {
        enabled = value;
    }
}
