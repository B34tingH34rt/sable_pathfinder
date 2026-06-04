package b34tingh34rt.sable_pathfinder;

import b34tingh34rt.sable_pathfinder.visualization.PathVisualizationState;
import b34tingh34rt.sable_pathfinder.network.PathGizmoPayload;
import b34tingh34rt.sable_pathfinder.visualization.PathGizmoState;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(SablePathfinder.MODID)
public class SablePathfinder {
    public static final String MODID = "sable_pathfinder";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SablePathfinder(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        LOGGER.info("Initializing Sable: Pathfinder");
    }

    private void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(PathGizmoPayload.TYPE, PathGizmoPayload.STREAM_CODEC, (payload, context) -> PathGizmoState.accept(payload));
    }

    private void registerCommands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sablepathfinder")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("visuals")
                        .executes(ctx -> {
                            final boolean enabled = !PathVisualizationState.isEnabled();
                            PathVisualizationState.setEnabled(enabled);
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.sable_pathfinder.visuals", enabled), true);
                            return 1;
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    final boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                    PathVisualizationState.setEnabled(enabled);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.sable_pathfinder.visuals", enabled), true);
                                    return 1;
                                }))));
    }
}
