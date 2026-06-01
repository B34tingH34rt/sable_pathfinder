package b34tingh34rt.sable_pathfinder.debug;

import java.util.Locale;

public final class MobPathDebugState {
    private static final boolean[] ENABLED = new boolean[Category.values().length];
    private static final Category[] PATH_RELATED = {
            Category.SOURCE,
            Category.TARGETS,
            Category.REMAP,
            Category.ACTUAL_PATH,
            Category.SEGMENTS,
            Category.CORRECTION,
            Category.REGION,
            Category.MOVEMENT,
            Category.PARTICLES,
            Category.NODE_LABELS
    };
    private static final Category[] ANALYSIS = {
            Category.SOURCE,
            Category.TARGETS,
            Category.REMAP,
            Category.ACTUAL_PATH,
            Category.SEGMENTS,
            Category.CORRECTION,
            Category.REGION,
            Category.MOVEMENT
    };

    private MobPathDebugState() {
    }

    public static boolean isEnabled() {
        for (final boolean enabled : ENABLED) {
            if (enabled) {
                return true;
            }
        }

        return false;
    }

    public static boolean isEnabled(final Category category) {
        return ENABLED[category.ordinal()];
    }

    public static void setEnabled(final boolean enabled) {
        for (final Category category : Category.values()) {
            setEnabled(category, enabled);
        }
    }

    public static void setEnabled(final Category category, final boolean enabled) {
        ENABLED[category.ordinal()] = enabled;
    }

    public static boolean toggle(final Category category) {
        final boolean enabled = !isEnabled(category);
        setEnabled(category, enabled);
        return enabled;
    }

    public static boolean toggleAll() {
        final boolean enabled = !isEnabled();
        setEnabled(enabled);
        return enabled;
    }

    public static boolean togglePathRelated() {
        final boolean enabled = !isPathRelatedEnabled();
        setPathRelatedEnabled(enabled);
        return enabled;
    }

    public static boolean toggleAnalysis() {
        final boolean enabled = !isAnalysisEnabled();
        setAnalysisEnabled(enabled);
        return enabled;
    }

    public static void setPathRelatedEnabled(final boolean enabled) {
        for (final Category category : PATH_RELATED) {
            setEnabled(category, enabled);
        }
    }

    public static boolean isPathRelatedEnabled() {
        for (final Category category : PATH_RELATED) {
            if (isEnabled(category)) {
                return true;
            }
        }

        return false;
    }

    public static void setAnalysisEnabled(final boolean enabled) {
        for (final Category category : ANALYSIS) {
            setEnabled(category, enabled);
        }
        setEnabled(Category.PARTICLES, false);
        setEnabled(Category.NODE_LABELS, false);
    }

    public static boolean isAnalysisEnabled() {
        for (final Category category : ANALYSIS) {
            if (isEnabled(category)) {
                return true;
            }
        }

        return false;
    }

    public static String describeStatus() {
        final StringBuilder builder = new StringBuilder();
        for (final Category category : Category.values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }

            builder.append(category.id)
                    .append("=")
                    .append(isEnabled(category));
        }

        return builder.toString();
    }

    public enum Category {
        SOURCE("source"),
        TARGETS("targets"),
        REMAP("remap"),
        ACTUAL_PATH("actual_path"),
        SEGMENTS("segments"),
        CORRECTION("correction"),
        REGION("region"),
        MOVEMENT("movement"),
        PARTICLES("particles"),
        NODE_LABELS("node_labels"),
        NODE_LABEL_INDEX("node_label_index"),
        NODE_LABEL_ORIGIN("node_label_origin"),
        NODE_LABEL_LOOKUP("node_label_lookup"),
        NODE_LABEL_NODE_POS("node_label_node_pos"),
        NODE_LABEL_LOOKUP_POS("node_label_lookup_pos");

        private final String id;

        Category(final String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public static Category byId(final String id) {
            final String normalized = id.toLowerCase(Locale.ROOT);
            for (final Category category : values()) {
                if (category.id.equals(normalized)) {
                    return category;
                }
            }

            throw new IllegalArgumentException("Unknown debug category: " + id);
        }
    }
}
