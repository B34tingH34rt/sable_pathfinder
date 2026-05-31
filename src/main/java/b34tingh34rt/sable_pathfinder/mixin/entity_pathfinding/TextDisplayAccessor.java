package b34tingh34rt.sable_pathfinder.mixin.entity_pathfinding;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
    @Invoker("setText")
    void sablePathfinder$setText(Component text);

    @Invoker("setLineWidth")
    void sablePathfinder$setLineWidth(int lineWidth);

    @Invoker("setBackgroundColor")
    void sablePathfinder$setBackgroundColor(int backgroundColor);
}
