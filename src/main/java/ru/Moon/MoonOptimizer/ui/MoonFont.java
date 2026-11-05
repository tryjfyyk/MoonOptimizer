package ru.Moon.MoonOptimizer.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import ru.Moon.MoonOptimizer.MoonOptimizerClient;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal true-type font renderer for the custom main menu.
 *
 * <p>The glyphs are rasterized with AWT into a texture atlas at
 * {@code pixelSize} device pixels and then drawn as tinted quads through the
 * vanilla GUI pipeline. {@code density} tells the renderer how many atlas
 * pixels make up one GUI unit, so the text stays pixel sharp at every GUI
 * scale instead of being upscaled from a low resolution atlas.
 */
public final class MoonFont {
    private static final int PADDING = 2;
    private static final int ATLAS_WIDTH = 2048;
    private static final AtomicInteger TEXTURE_COUNTER = new AtomicInteger();

    private final String resourcePath;
    private final int pixelSize;
    private final int density;
    private final String charset;

    private final Map<Character, Glyph> glyphs = new HashMap<>();

    private Identifier textureId;
    private int textureWidth;
    private int textureHeight;
    private int ascentPixels;
    private int descentPixels;

    private boolean built;
    private boolean failed;

    public MoonFont(String resourcePath, int pixelSize, int density, String charset) {
        this.resourcePath = resourcePath;
        this.pixelSize = Math.max(6, pixelSize);
        this.density = Math.max(1, density);
        this.charset = unique(charset);
    }

    /** Width of the text in GUI units. */
    public float width(String text) {
        ensureBuilt();

        float width = 0f;

        for (int i = 0; i < text.length(); i++) {
            Glyph glyph = glyphs.get(text.charAt(i));
            width += glyph == null ? pixelSize * 0.5f : glyph.advance;
        }

        return width / density;
    }

    /** Distance from the top of the glyph cell to the baseline, in GUI units. */
    public float baselineOffset() {
        ensureBuilt();
        return (PADDING + ascentPixels) / (float) density;
    }

    /** Ascent of the font in GUI units, used to center text optically. */
    public float ascent() {
        ensureBuilt();
        return ascentPixels / (float) density;
    }

    /** Descent of the font in GUI units. */
    public float descent() {
        ensureBuilt();
        return descentPixels / (float) density;
    }

    /** Full height of a glyph cell, in GUI units. */
    public float height() {
        ensureBuilt();
        return (ascentPixels + descentPixels + PADDING * 2) / (float) density;
    }

    public void draw(GuiGraphics graphics, String text, float x, float y, int color) {
        ensureBuilt();

        if (textureId == null) {
            return;
        }

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(1f / density, 1f / density);

        float penX = x * density;
        float penY = y * density;

        for (int i = 0; i < text.length(); i++) {
            Glyph glyph = glyphs.get(text.charAt(i));

            if (glyph == null) {
                penX += pixelSize * 0.5f;
                continue;
            }

            if (glyph.sourceWidth > 0 && glyph.sourceHeight > 0) {
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        textureId,
                        Math.round(penX + glyph.offsetX),
                        Math.round(penY),
                        glyph.u,
                        glyph.v,
                        glyph.sourceWidth,
                        glyph.sourceHeight,
                        glyph.sourceWidth,
                        glyph.sourceHeight,
                        textureWidth,
                        textureHeight,
                        color
                );
            }

            penX += glyph.advance;
        }

        pose.popMatrix();
    }

    /** Releases the GPU texture of an evicted font. */
    public void dispose() {
        if (textureId != null) {
            Minecraft.getInstance().getTextureManager().release(textureId);
            textureId = null;
        }
    }

    private void ensureBuilt() {
        if (built || failed) {
            return;
        }

        built = true;

        try {
            build();
        } catch (Exception exception) {
            failed = true;
            MoonOptimizerClient.LOGGER.warn("Could not build font {}", resourcePath, exception);
        }
    }

    private void build() throws Exception {
        java.awt.Font baseFont;

        try (InputStream input = MoonFont.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing font resource " + resourcePath);
            }

            baseFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, input);
        }

        java.awt.Font font = baseFont.deriveFont((float) pixelSize);

        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D scratchGraphics = scratch.createGraphics();
        scratchGraphics.setFont(font);
        FontMetrics metrics = scratchGraphics.getFontMetrics();
        scratchGraphics.dispose();

        ascentPixels = metrics.getAscent();
        descentPixels = metrics.getDescent();

        int glyphHeight = ascentPixels + descentPixels + PADDING * 2;

        List<Layout> layouts = new ArrayList<>();

        for (int i = 0; i < charset.length(); i++) {
            char character = charset.charAt(i);
            int advance = Math.max(1, metrics.charWidth(character));
            int glyphWidth = advance + PADDING * 2;

            layouts.add(new Layout(character, advance, glyphWidth, glyphHeight));
        }

        int cursorX = 0;
        int cursorY = 0;

        for (Layout layout : layouts) {
            if (cursorX + layout.width > ATLAS_WIDTH) {
                cursorX = 0;
                cursorY += glyphHeight;
            }

            layout.x = cursorX;
            layout.y = cursorY;

            cursorX += layout.width;
        }

        int atlasHeight = nextPowerOfTwo(Math.max(32, cursorY + glyphHeight));

        BufferedImage atlas = new BufferedImage(ATLAS_WIDTH, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D atlasGraphics = atlas.createGraphics();
        atlasGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        atlasGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        atlasGraphics.setFont(font);
        atlasGraphics.setColor(Color.WHITE);

        for (Layout layout : layouts) {
            atlasGraphics.drawString(
                    String.valueOf(layout.character),
                    layout.x + PADDING,
                    layout.y + PADDING + ascentPixels
            );

            Glyph glyph = new Glyph();
            glyph.u = layout.x;
            glyph.v = layout.y;
            glyph.sourceWidth = layout.width;
            glyph.sourceHeight = layout.height;
            glyph.offsetX = -PADDING;
            glyph.advance = layout.advance;
            glyphs.put(layout.character, glyph);
        }

        atlasGraphics.dispose();

        textureWidth = ATLAS_WIDTH;
        textureHeight = atlasHeight;
        textureId = MoonUi.upload("font_" + TEXTURE_COUNTER.incrementAndGet(), atlas);
    }

    private static int nextPowerOfTwo(int value) {
        int result = 1;

        while (result < value) {
            result <<= 1;
        }

        return result;
    }

    private static String unique(String text) {
        Set<Character> characters = new LinkedHashSet<>();

        for (int i = 0; i < text.length(); i++) {
            characters.add(text.charAt(i));
        }

        StringBuilder builder = new StringBuilder(characters.size());

        for (char character : characters) {
            builder.append(character);
        }

        return builder.toString();
    }

    private static final class Layout {
        private final char character;
        private final int advance;
        private final int width;
        private final int height;
        private int x;
        private int y;

        private Layout(char character, int advance, int width, int height) {
            this.character = character;
            this.advance = advance;
            this.width = width;
            this.height = height;
        }
    }

    private static final class Glyph {
        private float u;
        private float v;
        private int sourceWidth;
        private int sourceHeight;
        private float offsetX;
        private float advance;
    }
}
