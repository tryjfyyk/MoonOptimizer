package ru.Moon.MoonOptimizer.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the custom main menu stop the client the same way the vanilla quit
 * button does: by clearing the running flag instead of calling shutdown code
 * from inside a click handler.
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("running")
    void moonoptimizer$setRunning(boolean running);
}
