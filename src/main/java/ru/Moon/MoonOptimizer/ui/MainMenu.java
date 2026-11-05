package ru.Moon.MoonOptimizer.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import ru.Moon.MoonOptimizer.mixin.MinecraftAccessor;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom main menu ported from the provided design.
 *
 * <p>The original used Montserrat for the logo and Suisse for the buttons.
 * Suisse is a commercial typeface, so Inter (also SIL OFL) is bundled instead.
 *
 * <p>All sizes below are design units on a 1000 x 525 canvas, which is how the
 * reference screenshot was rendered (GUI scale 2). The whole menu is scaled to
 * the current window height, so it keeps the reference proportions on any
 * resolution and any GUI scale.
 */
public class MainMenu extends Screen {
    private static final String LOGO = "MoonOptimizer";

    private static final String[] LABELS = {
            "Одиночная игра",
            "Сетевая игра",
            "Настройки",
            "Аккаунты",
            "Выход"
    };

    private static final String BUTTON_CHARSET = String.join("", LABELS) + "A ";

    private static final String LOGO_FONT =
            "/assets/moonoptimizer/fonts/montserrat-semibold.ttf";
    private static final String BUTTON_FONT =
            "/assets/moonoptimizer/fonts/inter-medium.ttf";

    /** Height of the design canvas; the layout was authored for 1000 x 525. */
    private static final float DESIGN_HEIGHT = 525f;

    private static final float ROW_WIDTH = 233f;
    private static final float BUTTON_WIDTH = 114f;
    private static final float BUTTON_HEIGHT = 24f;
    private static final float BUTTON_GAP = 5f;
    private static final float SMALL_HEIGHT = 20f;
    private static final float SMALL_GAP = 4f;
    private static final float RADIUS = 5f;

    private static final float LOGO_SIZE = 24f;
    private static final float LOGO_GAP = 12f;
    private static final float BUTTON_TEXT_SIZE = 10f;

    private final float[] hover = new float[5];

    /** Small cache of rasterized fonts; keyed by resource, pixel size and density. */
    private static final Map<String, MoonFont> FONTS = new LinkedHashMap<>(8, 0.75f, true);

    public MainMenu() {
        super(Component.literal("Main Menu"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        float scale = scale();
        float designWidth = width / scale;
        float designHeight = height / scale;

        MoonBackdrop.render(
                graphics,
                width,
                height,
                scale,
                designWidth,
                designHeight,
                System.currentTimeMillis()
        );

        float row1Y = designHeight / 2f - 18f;

        MoonFont logo = font(LOGO_FONT, LOGO_SIZE, scale, LOGO);
        float logoWidth = logo.width(LOGO);
        float logoBaseline = (row1Y - LOGO_GAP) * scale;

        logo.draw(
                graphics,
                LOGO,
                (width - logoWidth) / 2f,
                logoBaseline - logo.baselineOffset(),
                0xFFFFFFFF
        );

        MoonFont button = font(BUTTON_FONT, BUTTON_TEXT_SIZE, scale, BUTTON_CHARSET);

        for (int i = 0; i < LABELS.length; i++) {
            float[] rect = buttonRect(i, scale, designWidth, designHeight);
            pill(
                    graphics,
                    button,
                    i,
                    mouseX,
                    mouseY,
                    rect[0],
                    rect[1],
                    rect[2],
                    rect[3],
                    LABELS[i],
                    i == 0,
                    RADIUS * scale
            );
        }
    }

    private void pill(GuiGraphics graphics, MoonFont font, int index,
                      int mouseX, int mouseY, float x, float y, float width, float height,
                      String label, boolean active, float radius) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        hover[index] = Math.max(0f, Math.min(1f, hover[index] + (hovered ? 0.12f : -0.12f)));

        float amount = hover[index];

        if (active) {
            MoonUi.roundedRect(graphics, x, y, width, height, radius, MoonUi.rgba(242, 242, 246, 255));
            centerText(graphics, font, label, x, y, width, height, MoonUi.rgba(18, 18, 24, 255));
            return;
        }

        int gradientTop = MoonUi.rgba(40, 40, 48, 220);
        int gradientBottom = MoonUi.rgba(24, 24, 31, 220);
        int border = MoonUi.rgba(255, 255, 255, (int) (45 + amount * 75));

        MoonUi.gradientRoundedRect(
                graphics, x, y, width, height, radius,
                gradientTop, gradientBottom, border
        );

        int text = amount > 0.5f
                ? MoonUi.rgba(240, 240, 245, 255)
                : MoonUi.rgba(160, 160, 172, 235);
        centerText(graphics, font, label, x, y, width, height, text);
    }

    /** Centers the label using the real font metrics, so it never drifts. */
    private void centerText(GuiGraphics graphics, MoonFont font, String text,
                            float x, float y, float width, float height, int color) {
        float textWidth = font.width(text);
        float ascent = font.ascent();
        float descent = font.descent();
        float baseline = y + (height - (ascent + descent)) / 2f + ascent;

        font.draw(graphics, text, x + (width - textWidth) / 2f, baseline - font.baselineOffset(), color);
    }

    private static boolean inside(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            float scale = scale();
            float designWidth = width / scale;
            float designHeight = height / scale;

            for (int i = 0; i < LABELS.length; i++) {
                float[] rect = buttonRect(i, scale, designWidth, designHeight);

                if (inside(event.x(), event.y(), rect[0], rect[1], rect[2], rect[3])) {
                    return press(i);
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private boolean press(int index) {
        switch (index) {
            case 0 -> this.minecraft.setScreen(new SelectWorldScreen(this));
            case 1 -> this.minecraft.setScreen(new JoinMultiplayerScreen(this));
            case 2 -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options));
            case 3 -> SystemToast.add(
                    this.minecraft.getToastManager(),
                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("MoonOptimizer"),
                    Component.translatable("moonoptimizer.menu.accounts.info")
            );
            default -> ((MinecraftAccessor) this.minecraft).moonoptimizer$setRunning(false);
        }

        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /** Scale from design units to GUI units for the current window. */
    private float scale() {
        float byHeight = Math.max(1f, height) / DESIGN_HEIGHT;
        float byWidth = Math.max(1f, width) / (ROW_WIDTH * 1.12f);
        float scale = Math.min(byHeight, byWidth);

        return Math.max(0.35f, Math.min(3.2f, scale));
    }

    /** Returns the GUI-space rectangle of the given button: x, y, width, height. */
    private static float[] buttonRect(int index, float scale, float designWidth, float designHeight) {
        float centerX = designWidth / 2f;
        float row1Y = designHeight / 2f - 18f;
        float row2Y = row1Y + BUTTON_HEIGHT + SMALL_GAP;
        float smallWidth = (ROW_WIDTH - 2f * SMALL_GAP) / 3f;
        float left = centerX - ROW_WIDTH / 2f;

        float x;
        float y;
        float width;
        float height;

        switch (index) {
            case 0 -> {
                x = centerX - BUTTON_WIDTH - BUTTON_GAP / 2f;
                y = row1Y;
                width = BUTTON_WIDTH;
                height = BUTTON_HEIGHT;
            }
            case 1 -> {
                x = centerX + BUTTON_GAP / 2f;
                y = row1Y;
                width = BUTTON_WIDTH;
                height = BUTTON_HEIGHT;
            }
            case 2 -> {
                x = left;
                y = row2Y;
                width = smallWidth;
                height = SMALL_HEIGHT;
            }
            case 3 -> {
                x = left + smallWidth + SMALL_GAP;
                y = row2Y;
                width = smallWidth;
                height = SMALL_HEIGHT;
            }
            default -> {
                x = left + 2f * (smallWidth + SMALL_GAP);
                y = row2Y;
                width = smallWidth;
                height = SMALL_HEIGHT;
            }
        }

        return new float[]{x * scale, y * scale, width * scale, height * scale};
    }

    /**
     * Rasterizes (or reuses) a font at the pixel size that matches the current
     * window, so glyphs are drawn one-to-one with screen pixels instead of being
     * stretched by the GUI scale.
     */
    private MoonFont font(String path, float designSize, float scale, String charset) {
        Minecraft minecraft = Minecraft.getInstance();
        int guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
        int pixelSize = Math.max(8, Math.round(designSize * scale * guiScale / 2f) * 2);
        String key = path + '@' + pixelSize + '#' + guiScale;

        MoonFont cached = FONTS.get(key);

        if (cached != null) {
            return cached;
        }

        cached = new MoonFont(path, pixelSize, guiScale, charset);
        FONTS.put(key, cached);

        while (FONTS.size() > 4) {
            Iterator<Map.Entry<String, MoonFont>> iterator = FONTS.entrySet().iterator();
            Map.Entry<String, MoonFont> eldest = iterator.next();
            iterator.remove();
            eldest.getValue().dispose();
        }

        return cached;
    }
}
