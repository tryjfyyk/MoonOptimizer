package ru.Moon.MoonOptimizer.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import ru.Moon.MoonOptimizer.MoonOptimizerClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Small drawing helpers used by the custom main menu: ARGB colors,
 * antialiased rounded rectangles, soft radial glows and dynamic textures.
 */
public final class MoonUi {
    private static final int MASK_SIZE = 32;
    private static final int MASK_RADIUS = 10;
    private static final int GLOW_SIZE = 256;

    private static Identifier maskTexture;
    private static Identifier glowTexture;
    private static boolean maskAttempted;
    private static boolean glowAttempted;

    private MoonUi() {
    }

    public static int rgba(int red, int green, int blue, int alpha) {
        return (clamp(alpha) << 24)
                | (clamp(red) << 16)
                | (clamp(green) << 8)
                | clamp(blue);
    }

    public static int mix(int first, int second, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));

        int a = lerp((first >>> 24) & 0xFF, (second >>> 24) & 0xFF, t);
        int r = lerp((first >>> 16) & 0xFF, (second >>> 16) & 0xFF, t);
        int g = lerp((first >>> 8) & 0xFF, (second >>> 8) & 0xFF, t);
        int b = lerp(first & 0xFF, second & 0xFF, t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void roundedRect(GuiGraphics graphics, float x, float y, float width, float height,
                                   float radius, int color) {
        ensureMask();

        if (maskTexture == null) {
            return;
        }

        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(width);
        int ih = Math.round(height);
        int r = Math.max(0, Math.round(radius));

        if (iw <= 0 || ih <= 0) {
            return;
        }

        if (r <= 0 || iw <= r * 2 || ih <= r * 2) {
            graphics.fill(ix, iy, ix + iw, iy + ih, color);
            return;
        }

        int inner = MASK_SIZE - MASK_RADIUS * 2;
        int far = MASK_SIZE - MASK_RADIUS;

        blit(graphics, ix, iy, 0, 0, r, r, MASK_RADIUS, MASK_RADIUS, color);
        blit(graphics, ix + iw - r, iy, far, 0, r, r, MASK_RADIUS, MASK_RADIUS, color);
        blit(graphics, ix, iy + ih - r, 0, far, r, r, MASK_RADIUS, MASK_RADIUS, color);
        blit(graphics, ix + iw - r, iy + ih - r, far, far, r, r, MASK_RADIUS, MASK_RADIUS, color);

        blit(graphics, ix + r, iy, MASK_RADIUS, 0, iw - r * 2, r, inner, MASK_RADIUS, color);
        blit(graphics, ix + r, iy + ih - r, MASK_RADIUS, far, iw - r * 2, r, inner, MASK_RADIUS, color);
        blit(graphics, ix, iy + r, 0, MASK_RADIUS, r, ih - r * 2, MASK_RADIUS, inner, color);
        blit(graphics, ix + iw - r, iy + r, far, MASK_RADIUS, r, ih - r * 2, MASK_RADIUS, inner, color);

        blit(graphics, ix + r, iy + r, MASK_RADIUS, MASK_RADIUS,
                iw - r * 2, ih - r * 2, inner, inner, color);
    }

    /**
     * Rounded rectangle filled with a vertical gradient and a translucent outline.
     * The straight section carries the gradient; the rounded caps use the
     * averaged color, which is indistinguishable at this radius.
     */
    public static void gradientRoundedRect(GuiGraphics graphics, float x, float y, float width, float height,
                                           float radius, int top, int bottom, int border) {
        roundedRect(graphics, x, y, width, height, radius, border);

        int middle = mix(top, bottom, 0.5f);
        roundedRect(graphics, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), middle);

        int ix = Math.round(x);
        int iy = Math.round(y);
        int iw = Math.round(width);
        int ih = Math.round(height);
        int r = Math.round(radius);

        if (iw - r * 2 > 0 && ih > 2) {
            graphics.fillGradient(
                    ix + r,
                    iy + 1,
                    ix + iw - r,
                    iy + ih - 1,
                    top,
                    bottom
            );
        }
    }

    /** Draws a soft circular glow, tinted with the supplied ARGB color. */
    public static void glow(GuiGraphics graphics, float centerX, float centerY, float diameter, int color) {
        ensureGlow();

        if (glowTexture == null) {
            return;
        }

        int size = Math.max(8, Math.round(diameter));

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                glowTexture,
                Math.round(centerX - size / 2f),
                Math.round(centerY - size / 2f),
                0f,
                0f,
                size,
                size,
                GLOW_SIZE,
                GLOW_SIZE,
                GLOW_SIZE,
                GLOW_SIZE,
                color
        );
    }

    /** Uploads a buffered image as a dynamic texture and returns its identifier. */
    public static Identifier upload(String name, BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);

        NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(output.toByteArray()));
        DynamicTexture texture = new DynamicTexture(() -> "MoonOptimizer " + name, nativeImage);
        texture.upload();

        Identifier id = Identifier.fromNamespaceAndPath(
                MoonOptimizerClient.MOD_ID,
                "dynamic/" + name
        );

        Minecraft.getInstance().getTextureManager().register(id, texture);
        return id;
    }

    private static void blit(GuiGraphics graphics, int x, int y, int u, int v,
                             int width, int height, int sourceWidth, int sourceHeight, int color) {
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                maskTexture,
                x,
                y,
                (float) u,
                (float) v,
                width,
                height,
                sourceWidth,
                sourceHeight,
                MASK_SIZE,
                MASK_SIZE,
                color
        );
    }

    private static void ensureMask() {
        if (maskAttempted) {
            return;
        }

        maskAttempted = true;

        BufferedImage image = new BufferedImage(MASK_SIZE, MASK_SIZE, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < MASK_SIZE; y++) {
            for (int x = 0; x < MASK_SIZE; x++) {
                int covered = 0;

                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        float fx = x + (sx + 0.5f) / 4f;
                        float fy = y + (sy + 0.5f) / 4f;

                        if (insideRoundedRect(fx, fy)) {
                            covered++;
                        }
                    }
                }

                int alpha = covered * 255 / 16;
                image.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }

        try {
            maskTexture = upload("rounded_mask", image);
        } catch (Exception exception) {
            MoonOptimizerClient.LOGGER.warn("Could not build rounded rectangle mask", exception);
        }
    }

    private static void ensureGlow() {
        if (glowAttempted) {
            return;
        }

        glowAttempted = true;

        BufferedImage image = new BufferedImage(GLOW_SIZE, GLOW_SIZE, BufferedImage.TYPE_INT_ARGB);
        float radius = GLOW_SIZE / 2f;

        for (int y = 0; y < GLOW_SIZE; y++) {
            for (int x = 0; x < GLOW_SIZE; x++) {
                float dx = (x + 0.5f - radius) / radius;
                float dy = (y + 0.5f - radius) / radius;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                float falloff = Math.max(0f, 1f - Math.min(1f, distance));
                int alpha = Math.round((float) Math.pow(falloff, 2.4f) * 255f);

                image.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }

        try {
            glowTexture = upload("radial_glow", image);
        } catch (Exception exception) {
            MoonOptimizerClient.LOGGER.warn("Could not build radial glow", exception);
        }
    }

    private static boolean insideRoundedRect(float x, float y) {
        float cx = Math.max(MASK_RADIUS, Math.min(MASK_SIZE - MASK_RADIUS, x));
        float cy = Math.max(MASK_RADIUS, Math.min(MASK_SIZE - MASK_RADIUS, y));

        float dx = x - cx;
        float dy = y - cy;

        return dx * dx + dy * dy <= (float) MASK_RADIUS * MASK_RADIUS;
    }

    private static int lerp(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
