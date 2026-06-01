package b34tingh34rt.sable_pathfinder;

import b34tingh34rt.sable_pathfinder.debug.MobPathDebugState;
import b34tingh34rt.sable_pathfinder.debug.MobPathDebugState.Category;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(SablePathfinder.MODID)
public class SablePathfinder {
    public static final String MODID = "sable_pathfinder";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final SuggestionProvider<CommandSourceStack> DEBUG_CATEGORY_SUGGESTIONS = (context, builder) -> {
        builder.suggest("all");
        builder.suggest("pathing");
        builder.suggest("analysis");
        for (final Category category : Category.values()) {
            builder.suggest(category.id());
        }

        return builder.buildFuture();
    };

    public SablePathfinder(final IEventBus modEventBus, final ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        LOGGER.info("Initializing Sable: Pathfinder");
    }

    private void registerCommands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sablepathfinder")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug_paths")
                        .executes(ctx -> {
                            final boolean enabled = MobPathDebugState.toggleAll();
                            this.sendDebugStatus(ctx.getSource(), "all", enabled);
                            return 1;
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    final boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                    MobPathDebugState.setEnabled(enabled);
                                    this.sendDebugStatus(ctx.getSource(), "all", enabled);
                                    return 1;
                                })))
                .then(Commands.literal("debug_pathing")
                        .executes(ctx -> {
                            final boolean enabled = MobPathDebugState.togglePathRelated();
                            this.sendDebugStatus(ctx.getSource(), "pathing", enabled);
                            return 1;
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    final boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                    MobPathDebugState.setPathRelatedEnabled(enabled);
                                    this.sendDebugStatus(ctx.getSource(), "pathing", enabled);
                                    return 1;
                                })))
                .then(Commands.literal("debug_analysis")
                        .executes(ctx -> {
                            final boolean enabled = MobPathDebugState.toggleAnalysis();
                            this.sendDebugStatus(ctx.getSource(), "analysis", enabled);
                            return 1;
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    final boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                    MobPathDebugState.setAnalysisEnabled(enabled);
                                    this.sendDebugStatus(ctx.getSource(), "analysis", enabled);
                                    return 1;
                                })))
                .then(Commands.literal("debug")
                        .executes(ctx -> {
                            this.sendDebugList(ctx.getSource());
                            return 1;
                        })
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    this.sendDebugList(ctx.getSource());
                                    return 1;
                                }))
                        .then(Commands.literal("pathing")
                                .executes(ctx -> {
                                    final boolean enabled = MobPathDebugState.togglePathRelated();
                                    this.sendDebugStatus(ctx.getSource(), "pathing", enabled);
                                    return 1;
                                })
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            final boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                            MobPathDebugState.setPathRelatedEnabled(enabled);
                                            this.sendDebugStatus(ctx.getSource(), "pathing", enabled);
                                            return 1;
                                        })))
                        .then(Commands.literal("analysis")
                                .executes(ctx -> {
                                    final boolean enabled = MobPathDebugState.toggleAnalysis();
                                    this.sendDebugStatus(ctx.getSource(), "analysis", enabled);
                                    return 1;
                                })
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            final boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                            MobPathDebugState.setAnalysisEnabled(enabled);
                                            this.sendDebugStatus(ctx.getSource(), "analysis", enabled);
                                            return 1;
                                        })))
                        .then(Commands.argument("category", StringArgumentType.word())
                                .suggests(DEBUG_CATEGORY_SUGGESTIONS)
                                .executes(ctx -> {
                                    final String categoryId = StringArgumentType.getString(ctx, "category");
                                    final boolean enabled = this.toggleCategory(categoryId);
                                    this.sendDebugStatus(ctx.getSource(), categoryId, enabled);
                                    return 1;
                                })
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            final String categoryId = StringArgumentType.getString(ctx, "category");
                                            final boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                            this.setCategory(categoryId, enabled);
                                            this.sendDebugStatus(ctx.getSource(), categoryId, enabled);
                                            return 1;
                                        })))));
    }

    private boolean toggleCategory(final String categoryId) {
        if ("all".equalsIgnoreCase(categoryId)) {
            return MobPathDebugState.toggleAll();
        }
        if ("pathing".equalsIgnoreCase(categoryId)) {
            return MobPathDebugState.togglePathRelated();
        }
        if ("analysis".equalsIgnoreCase(categoryId)) {
            return MobPathDebugState.toggleAnalysis();
        }

        return MobPathDebugState.toggle(Category.byId(categoryId));
    }

    private void setCategory(final String categoryId, final boolean enabled) {
        if ("all".equalsIgnoreCase(categoryId)) {
            MobPathDebugState.setEnabled(enabled);
            return;
        }
        if ("pathing".equalsIgnoreCase(categoryId)) {
            MobPathDebugState.setPathRelatedEnabled(enabled);
            return;
        }
        if ("analysis".equalsIgnoreCase(categoryId)) {
            MobPathDebugState.setAnalysisEnabled(enabled);
            return;
        }

        MobPathDebugState.setEnabled(Category.byId(categoryId), enabled);
    }

    private void sendDebugStatus(final CommandSourceStack source, final String categoryId, final boolean enabled) {
        source.sendSuccess(() -> Component.literal("Sable Pathfinder debug " + categoryId + ": " + enabled), true);
    }

    private void sendDebugList(final CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Sable Pathfinder debug categories: " + MobPathDebugState.describeStatus()), false);
    }
}
