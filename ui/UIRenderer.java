package com.livemine.ui;

import com.livemine.LiveMineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Утилита рендера UI (ТЗ 20.0 §37).
 *
 * Используется клиентскими классами для:
 *   - Полосок потребностей над NPC (в разработке)
 *   - Пузырей мыслей (в разработке)
 *   - Отладочных оверлеев
 *
 * РџСЂРёРІСЏР·РєР° Рє RenderLevelStageEvent РІ ClientEventHandler.
 */
@OnlyIn(Dist.CLIENT)
public final class UIRenderer {

    private UIRenderer() {}

    public static final int COLOR_HEALTH = 0xFF5555;
    public static final int COLOR_HUNGER = 0xFFAA00;
    public static final int COLOR_ENERGY = 0xFF55F55;
    public static final int COLOR_SOCIAL = 0x5555FF;
    public static final int COLOR_BG = 0x55000000;
    public static final int COLOR_BORDER = 0xFF888888;
    public static final int COLOR_TEXT = 0xFFFFFFFF;

    // =========================================================================
    // Полоски
    // =========================================================================

    /**
     * Рисует горизонтальную полосу с процентами.
     *
     * @param gfx     GuiGraphics
     * @param x       X экрана
     * @param y       Y экрана
     * @param width   ширина
     * @param height  высота
     * @param value   текущее (0..max)
     * @param max     максимум
     * @param color   цвет заполнения
     */
    public static void drawBar(GuiGraphics gfx, int x, int y, int width, int height,
                                double value, double max, int color) {
        if (gfx == null) return;

        // Фон
        gfx.fill(x, y, x + width, y + height, COLOR_BG);

        // Заполнение
        int fillWidth = max > 0
            ? (int) (width * Math.min(1.0, Math.max(0, value / max)))
            : 0;
        if (fillWidth > 0) {
            gfx.fill(x, y, x + fillWidth, y + height, 0xFF000000 | color);
        }

        // Рамка
        gfx.renderOutline(x, y, width, height, COLOR_BORDER);
    }

    /**
     * Рисует полосу с подписью.
     */
    public static void drawLabeledBar(GuiGraphics gfx, int x, int y, int width,
                                       String label, double value, double max, int color) {
        if (gfx == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        gfx.drawString(mc.font, label + ": " + (int) value + "/" + (int) max,
            x, y - 10, COLOR_TEXT, false);
        drawBar(gfx, x, y, width, 12, value, max, color);
    }

    // =========================================================================
    // Текстовые оверлеи
    // =========================================================================

    public static void drawTitle(GuiGraphics gfx, String title, int centerX, int y) {
        if (gfx == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        gfx.drawCenteredString(mc.font,
            net.minecraft.network.chat.Component.literal("В§6В§l" + title),
            centerX, y, 0xFFD700);
    }

    public static void drawLabelValue(GuiGraphics gfx, String label, String value,
                                       int x, int y, int color) {
        if (gfx == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        gfx.drawString(mc.font, "В§7" + label + ": В§r" + value,
            x, y, color, false);
    }

    // =========================================================================
    // Полоса здоровья над NPC
    // =========================================================================

    /**
     * Рисует простую полоску здоровья над указанным экранным X/Y.
     */
    public static void drawHealthBar(GuiGraphics gfx, int x, int y, int width,
                                      float current, float max) {
        drawBar(gfx, x, y, width, 4, current, max, COLOR_HEALTH);
    }

    // =========================================================================
    // Отладочный оверлей
    // =========================================================================

    /**
     * Отладочный блок для /lm debug.
     */
    public static void drawDebugPanel(GuiGraphics gfx, int x, int y, String[] lines) {
        if (gfx == null || lines == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        int bgHeight = lines.length * 12 + 8;
        gfx.fill(x - 4, y - 4, x + 220, y + bgHeight, 0xAA000000);

        for (int i = 0; i < lines.length; i++) {
            gfx.drawString(mc.font, lines[i], x, y + i * 12, 0xFFFFFF, false);
        }
    }

    // =========================================================================
    // Проверки
    // =========================================================================

    public static boolean shouldRenderNames() {
        return LiveMineConfig.showNpcNames();
    }

    public static boolean shouldRenderHealthBar() {
        return LiveMineConfig.showNpcHealthBar();
    }
}
