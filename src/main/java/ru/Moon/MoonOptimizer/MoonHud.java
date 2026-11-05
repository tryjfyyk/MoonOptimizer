package ru.Moon.MoonOptimizer;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

public final class MoonHud {
    private static final long STATS_REFRESH_MS = 250L;

    private static final int COLOR_TEXT = 0xFFEAFBFF;
    private static final int COLOR_SECONDARY = 0xFF9CB8C7;
    private static final int COLOR_GOOD = 0xFF7DFFB2;
    private static final int COLOR_WARN = 0xFFFFD866;
    private static final int COLOR_BAD = 0xFFFF7A7A;

    private static final Component TITLE = Component.literal("MoonOptimizer");

    private static long lastStatsRefreshMs = 0L;
    private static Component performanceText = Component.empty();
    private static Component positionText = Component.empty();

    private static MoonProfile cachedProfile;
    private static boolean cachedAuto;
    private static Component profileText = Component.empty();
    private static int profileColor = COLOR_TEXT;

    private MoonHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MoonOptimizerClient.MOD_ID, "hud"),
                MoonHud::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;

        if (!MoonOptimizerClient.hudVisible()
                || client.options.hideGui
                || player == null
                || client.screen != null) {
            return;
        }

        int x = 8;
        int y = 8;
        int width = 190;
        int height = 72;

        graphics.fill(x, y, x + width, y + height, 0xB30E1625);
        graphics.fill(x, y, x + width, y + 1, 0xFF78E8FF);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF1A5265);

        drawAnimatedMoon(graphics, x + 24, y + 28);

        graphics.drawString(client.font, TITLE, x + 46, y + 8, COLOR_TEXT, true);

        refreshProfile();
        graphics.drawString(client.font, profileText, x + 46, y + 21, profileColor, true);

        refreshStats(client, player);

        graphics.drawString(
                client.font,
                performanceText,
                x + 8,
                y + 45,
                fpsColor(client.getFps()),
                false
        );

        graphics.drawString(
                client.font,
                positionText,
                x + 8,
                y + 58,
                COLOR_SECONDARY,
                false
        );
    }

    private static void refreshProfile() {
        MoonProfile profile = MoonOptimizerClient.activeProfile();
        boolean auto = MoonOptimizerClient.autoApplied();

        if (profile == cachedProfile && auto == cachedAuto) {
            return;
        }

        cachedProfile = profile;
        cachedAuto = auto;

        Component name = Component.translatable(profile.translationKey());
        profileText = auto
                ? Component.translatable("moonoptimizer.hud.auto", name)
                : name;

        profileColor = switch (profile) {
            case QUALITY -> 0xFF78E8FF;
            case BALANCED -> COLOR_WARN;
            case PERFORMANCE -> COLOR_GOOD;
            case EXTREME -> 0xFFFF9F4A;
        };
    }

    private static int fpsColor(int fps) {
        int target = MoonOptimizerClient.fpsTarget();

        if (fps >= target) {
            return COLOR_GOOD;
        }

        return fps >= target * 0.7f ? COLOR_WARN : COLOR_BAD;
    }

    private static void refreshStats(Minecraft client, Player player) {
        long now = Util.getMillis();

        if (now - lastStatsRefreshMs < STATS_REFRESH_MS) {
            return;
        }

        lastStatsRefreshMs = now;

        performanceText = Component.literal(
                "FPS " + client.getFps()
                        + "   MEM " + getUsedMemory() + " MB " + getUsedMemoryPercent() + "%"
        );

        positionText = Component.literal(
                "XYZ "
                        + player.getBlockX() + " "
                        + player.getBlockY() + " "
                        + player.getBlockZ()
                        + "   PING " + getPing(client)
        );
    }

    private static void drawAnimatedMoon(GuiGraphics graphics, int centerX, int centerY) {
        double time = Util.getMillis() / 1000.0;
        float pulse = 0.5f + 0.5f * Mth.sin((float) (time * 2.4));

        int glowAlpha = 22 + (int) (pulse * 22.0f);

        // Soft square glow. It is intentionally drawn without textures.
        graphics.fill(
                centerX - 13,
                centerY - 13,
                centerX + 13,
                centerY + 13,
                argb(glowAlpha, 0x70DFFF)
        );

        graphics.fill(
                centerX - 10,
                centerY - 10,
                centerX + 10,
                centerY + 10,
                argb(glowAlpha + 12, 0xA9F3FF)
        );

        // Pixel-art moon.
        int moonColor = 0xFFF4FCFF;

        graphics.fill(centerX - 5, centerY - 10, centerX + 5, centerY + 10, moonColor);
        graphics.fill(centerX - 8, centerY - 7, centerX + 8, centerY + 7, moonColor);
        graphics.fill(centerX - 10, centerY - 3, centerX + 10, centerY + 4, moonColor);

        // Cutout creates the crescent shape.
        int cutout = 0xFF142231;

        graphics.fill(centerX - 1, centerY - 9, centerX + 9, centerY - 4, cutout);
        graphics.fill(centerX + 1, centerY - 4, centerX + 10, centerY + 5, cutout);
        graphics.fill(centerX - 1, centerY + 5, centerX + 8, centerY + 9, cutout);

        // Small stars rotate around the moon.
        for (int i = 0; i < 3; i++) {
            double angle = time * 0.7 + i * 2.1;
            int radius = 15 + i * 2;
            int starX = centerX + (int) (Mth.cos((float) angle) * radius);
            int starY = centerY + (int) (Mth.sin((float) angle) * radius);

            graphics.fill(starX, starY, starX + 2, starY + 2, 0xFFE8FBFF);
        }
    }

    private static int getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();

        long used = runtime.totalMemory() - runtime.freeMemory();
        return (int) (used / 1024L / 1024L);
    }

    private static int getUsedMemoryPercent() {
        Runtime runtime = Runtime.getRuntime();

        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();

        return max <= 0L ? 0 : (int) (used * 100L / max);
    }

    private static int getPing(Minecraft client) {
        if (client.getConnection() == null || client.player == null) {
            return 0;
        }

        PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());

        return info == null ? 0 : info.getLatency();
    }

    private static int argb(int alpha, int rgb) {
        return ((alpha & 255) << 24) | (rgb & 0xFFFFFF);
    }
}
