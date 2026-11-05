package ru.Moon.MoonOptimizer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MoonOptimizerClient implements ClientModInitializer {
    public static final String MOD_ID = "moonoptimizer";
    public static final Logger LOGGER = LoggerFactory.getLogger("MoonOptimizer");

    /** How long FPS must stay low before the auto mode switches to PERFORMANCE. */
    private static final long AUTO_DOWN_DELAY_MS = 3_000L;
    /** How long FPS must stay healthy before the auto mode restores the chosen profile. */
    private static final long AUTO_UP_DELAY_MS = 6_000L;
    /** FPS must exceed the threshold by this factor before auto mode backs off. */
    private static final double AUTO_UP_MARGIN = 1.25;

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(MOD_ID, "main")
            );

    private static MoonSettings settings = new MoonSettings();
    private static MoonProfile desiredProfile = MoonProfile.QUALITY;
    private static MoonProfile activeProfile = MoonProfile.QUALITY;
    private static boolean autoApplied = false;
    private static boolean pendingInitialApply = true;

    private static long lowFpsMs;
    private static long highFpsMs;
    private static long lastAutoCheckMs;

    private static KeyMapping toggleHudKey;
    private static KeyMapping cycleProfileKey;
    private static KeyMapping toggleAutoKey;

    @Override
    public void onInitializeClient() {
        settings = MoonSettings.load();
        desiredProfile = settings.profile();
        activeProfile = desiredProfile;

        toggleHudKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.moonoptimizer.toggle_hud",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_F7,
                        CATEGORY
                )
        );

        cycleProfileKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.moonoptimizer.cycle_profile",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_F8,
                        CATEGORY
                )
        );

        toggleAutoKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.moonoptimizer.toggle_auto",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_F9,
                        CATEGORY
                )
        );

        MoonHud.register();

        ClientTickEvents.END_CLIENT_TICK.register(MoonOptimizerClient::onEndTick);

        // Profiles rewrite the player's video settings, so they must be restored
        // before vanilla writes options.txt on shutdown.
        ClientLifecycleEvents.CLIENT_STOPPING.register(MoonOptimizerClient::onStopping);
    }

    private static void onEndTick(Minecraft client) {
        if (pendingInitialApply) {
            pendingInitialApply = false;
            applyGuiScale(client);
            applyProfile(client, desiredProfile);
        }

        while (toggleHudKey.consumeClick()) {
            settings.hudVisible = !settings.hudVisible;
            settings.save();
        }

        while (cycleProfileKey.consumeClick()) {
            cycleProfile(client);
        }

        while (toggleAutoKey.consumeClick()) {
            settings.autoBoost = !settings.autoBoost;
            resetAutoTimers();
            settings.save();
            showToast(
                    client,
                    Component.translatable(
                            "moonoptimizer.toast.auto",
                            Component.translatable(settings.autoBoost
                                    ? "moonoptimizer.toast.on"
                                    : "moonoptimizer.toast.off")
                    )
            );
        }

        tickAutoMode(client);
    }

    private static void cycleProfile(Minecraft client) {
        desiredProfile = desiredProfile.next();
        settings.setProfile(desiredProfile);
        autoApplied = false;
        resetAutoTimers();
        applyProfile(client, desiredProfile);
        settings.save();
        showToast(
                client,
                Component.translatable(
                        "moonoptimizer.toast.profile",
                        Component.translatable(desiredProfile.translationKey())
                )
        );
    }

    private static void applyProfile(Minecraft client, MoonProfile profile) {
        try {
            MoonOptions.apply(client, profile);
            activeProfile = profile;
        } catch (RuntimeException exception) {
            LOGGER.error("Could not apply profile {}", profile.id(), exception);
        }
    }

    /**
     * The menu scales itself to the window height, so a GUI scale of 1 gives the
     * same menu size as the reference while leaving more room for the in-game
     * HUD. The option stays user-editable: this only runs on startup.
     */
    private static void applyGuiScale(Minecraft client) {
        if (!settings.autoGuiScale) {
            return;
        }

        if (client.options.guiScale().get() == 1) {
            return;
        }

        try {
            client.options.guiScale().set(1);
            client.resizeDisplay();
            LOGGER.info("Set GUI scale to 1 (autoGuiScale)");
        } catch (RuntimeException exception) {
            LOGGER.error("Could not set GUI scale", exception);
        }
    }

    private static void tickAutoMode(Minecraft client) {
        if (!settings.autoBoost || client.player == null || client.screen != null) {
            resetAutoTimers();
            return;
        }

        long now = Util.getMillis();
        long delta = lastAutoCheckMs == 0L ? 0L : now - lastAutoCheckMs;
        lastAutoCheckMs = now;

        // Ignore frame stalls and pauses instead of counting them as low FPS.
        if (delta <= 0L || delta > 1_000L) {
            delta = 0L;
        }

        int fps = client.getFps();
        int target = settings.autoBoostFpsThreshold;

        if (fps < target) {
            lowFpsMs += delta;
            highFpsMs = 0L;
        } else if (fps > target * AUTO_UP_MARGIN) {
            highFpsMs += delta;
            lowFpsMs = 0L;
        } else {
            lowFpsMs = 0L;
            highFpsMs = 0L;
        }

        if (lowFpsMs >= AUTO_DOWN_DELAY_MS && activeProfile != MoonProfile.PERFORMANCE) {
            autoApplied = true;
            applyProfile(client, MoonProfile.PERFORMANCE);
        }

        if (highFpsMs >= AUTO_UP_DELAY_MS && autoApplied) {
            autoApplied = false;
            applyProfile(client, desiredProfile);
        }
    }

    private static void resetAutoTimers() {
        lowFpsMs = 0L;
        highFpsMs = 0L;
        lastAutoCheckMs = 0L;
    }

    private static void onStopping(Minecraft client) {
        MoonOptions.restore(client);
        settings.save();
        client.options.save();
    }

    private static void showToast(Minecraft client, Component message) {
        SystemToast.add(
                client.getToastManager(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("moonoptimizer.toast.title"),
                message
        );
    }

    public static boolean hudVisible() {
        return settings.hudVisible;
    }

    public static MoonProfile activeProfile() {
        return activeProfile;
    }

    public static boolean autoApplied() {
        return autoApplied;
    }

    public static int fpsTarget() {
        return settings.autoBoostFpsThreshold;
    }
}
