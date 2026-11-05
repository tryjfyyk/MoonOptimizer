package ru.Moon.MoonOptimizer.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.Moon.MoonOptimizer.ui.MainMenu;

/**
 * Replaces the vanilla title screen with the MoonOptimizer main menu.
 * Every {@code new TitleScreen()} that reaches {@code Minecraft#setScreen}
 * (startup, disconnect, quitting a world) is redirected.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void moonoptimizer$replaceTitleScreen(Screen screen, CallbackInfo callback) {
        if (screen instanceof TitleScreen) {
            Minecraft minecraft = (Minecraft) (Object) this;
            callback.cancel();
            minecraft.setScreen(new MainMenu());
        }
    }
}
