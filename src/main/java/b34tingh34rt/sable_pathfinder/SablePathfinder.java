package b34tingh34rt.sable_pathfinder;

import b34tingh34rt.sable_pathfinder.debug.MobPathDebugState;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.logging.LogUtils;
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

    public SablePathfinder(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        LOGGER.info("Initializing Sable: Pathfinder");
    }

    private void registerCommands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sablepathfinder")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug_paths")
                        .executes(ctx -> {
                            final boolean enabled = !MobPathDebugState.isEnabled();
                            MobPathDebugState.setEnabled(enabled);
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.sable_pathfinder.debug_paths", enabled), true);
                            return 1;
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    final boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                    MobPathDebugState.setEnabled(enabled);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.sable_pathfinder.debug_paths", enabled), true);
                                    return 1;
                                }))));
    }
}