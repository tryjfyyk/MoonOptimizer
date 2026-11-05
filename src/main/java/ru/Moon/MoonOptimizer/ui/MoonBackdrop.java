package ru.Moon.MoonOptimizer.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import ru.Moon.MoonOptimizer.MoonOptimizerClient;

import java.awt.image.BufferedImage;

/**
 * Animated night sky for the main menu: the same dark gradient as the
 * reference design, plus a vanilla-style moon, a soft glow and a few stars so
 * the screen no longer feels empty.
 */
public final class MoonBackdrop {
    private static final int MOON_TEXTURE_SIZE = 32;
    private static final int STAR_COUNT = 96;

    /** Moon position and size, in design units (1000 x 525 canvas). */
    private static final float MOON_X = 0.775f;
    private static final float MOON_Y = 0.225f;
    private static final float MOON_SIZE = 118f;
    private static final float GLOW_SCALE = 2.9f;

    private static Identifier moonTexture;
    private static boolean moonAttempted;

    private MoonBackdrop() {
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight,
                              float scale, float designWidth, float designHeight, long millis) {
        double time = millis / 1000.0;
        float shift = (float) (0.5 + 0.5 * Math.sin(time * 0.6));

        int top = MoonUi.rgba(
                13 + (int) (shift * 9),
                13 + (int) (shift * 9),
                18 + (int) (shift * 12),
                255
        );
        int bottom = MoonUi.rgba(
                23 - (int) (shift * 9),
                23 - (int) (shift * 9),
                30 - (int) (shift * 12),
                255
        );

        graphics.fillGradient(0, 0, screenWidth, screenHeight, top, bottom);

        float moonCenterX = designWidth * MOON_X * scale;
        float moonCenterY = designHeight * MOON_Y * scale;
        float moonSize = MOON_SIZE * scale;

        drawStars(graphics, scale, designWidth, designHeight, time);
        MoonUi.glow(graphics, moonCenterX, moonCenterY, moonSize * GLOW_SCALE,
                MoonUi.rgba(158, 178, 235, 34));
        MoonUi.glow(graphics, moonCenterX, moonCenterY, moonSize * 1.35f,
                MoonUi.rgba(196, 210, 245, 26));

        Identifier moon = moonTexture();

        if (moon != null) {
            int size = Math.round(moonSize);
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    moon,
                    Math.round(moonCenterX - size / 2f),
                    Math.round(moonCenterY - size / 2f),
                    0f,
                    0f,
                    size,
                    size,
                    MOON_TEXTURE_SIZE,
                    MOON_TEXTURE_SIZE,
                    MOON_TEXTURE_SIZE,
                    MOON_TEXTURE_SIZE,
                    0xFFFFFFFF
            );
        }
    }

    private static void drawStars(GuiGraphics graphics, float scale, float designWidth,
                                  float designHeight, double time) {
        for (int i = 0; i < STAR_COUNT; i++) {
            float x = hash(i, 11) * designWidth;
            float y = hash(i, 23) * designHeight * 0.78f;
            float phase = hash(i, 37) * 6.2831853f;

            // Keep the star field away from the moon so the glow stays clean.
            float moonX = designWidth * MOON_X;
            float moonY = designHeight * MOON_Y;

            if (Math.abs(x - moonX) < 95f && Math.abs(y - moonY) < 95f) {
                continue;
            }

            float twinkle = 0.55f + 0.45f * (float) Math.sin(time * 0.9 + phase);
            int alpha = Math.round((26f + 54f * hash(i, 53)) * twinkle);

            if (alpha <= 3) {
                continue;
            }

            int size = Math.max(1, Math.round((hash(i, 71) > 0.82f ? 2f : 1.3f) * scale));
            int color = MoonUi.rgba(206, 219, 255, alpha);

            graphics.fill(
                    Math.round(x * scale),
                    Math.round(y * scale),
                    Math.round(x * scale) + size,
                    Math.round(y * scale) + size,
                    color
            );
        }
    }

    /** Stable pseudo random value in [0, 1) for the given star index and salt. */
    private static float hash(int index, int salt) {
        int value = index * 374761393 + salt * 668265263;
        value = (value ^ (value >>> 13)) * 1274126177;
        value = value ^ (value >>> 16);

        return (value & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    private static Identifier moonTexture() {
        if (moonAttempted) {
            return moonTexture;
        }

        moonAttempted = true;

        BufferedImage image = new BufferedImage(
                MOON_TEXTURE_SIZE,
                MOON_TEXTURE_SIZE,
                BufferedImage.TYPE_INT_ARGB
        );

        double center = (MOON_TEXTURE_SIZE - 1) / 2.0;
        double radius = MOON_TEXTURE_SIZE / 2.0 - 0.9;

        double[][] craters = {
                {10.5, 10.8, 2.8},
                {18.6, 10.2, 1.9},
                {17.4, 19.0, 3.1},
                {9.6, 19.4, 1.7},
                {15.4, 15.2, 1.3}
        };

        for (int y = 0; y < MOON_TEXTURE_SIZE; y++) {
            for (int x = 0; x < MOON_TEXTURE_SIZE; x++) {
                double dx = x - center;
                double dy = y - center;
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance > radius + 0.7) {
                    continue;
                }

                int alpha = distance > radius ? 110 : 255;

                float shade = 0.86f + 0.14f * (float) Math.max(0.0, Math.min(1.0,
                        (-(dx + dy)) / (2.0 * radius) + 0.5));

                int red = Math.round(214 * shade);
                int green = Math.round(219 * shade);
                int blue = Math.round(228 * shade);

                for (double[] crater : craters) {
                    double cdx = x - crater[0];
                    double cdy = y - crater[1];

                    if (cdx * cdx + cdy * cdy <= crater[2] * crater[2]) {
                        red = Math.round(172 * shade);
                        green = Math.round(177 * shade);
                        blue = Math.round(190 * shade);
                        break;
                    }
                }

                if (distance > radius - 1.2 && distance <= radius) {
                    red = Math.round(188 * shade);
                    green = Math.round(193 * shade);
                    blue = Math.round(204 * shade);
                }

                image.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }

        try {
            moonTexture = MoonUi.upload("menu_moon", image);
        } catch (Exception exception) {
            MoonOptimizerClient.LOGGER.warn("Could not build moon texture", exception);
        }

        return moonTexture;
    }
}
