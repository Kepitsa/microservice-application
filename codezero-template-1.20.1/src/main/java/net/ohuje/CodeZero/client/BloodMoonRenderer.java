package net.ohuje.CodeZero.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/**
 * Рисует полупрозрачный красный оверлей через HudRenderCallback (поверх всего),
 * но благодаря альфа-каналу HUD остаётся видимым.
 */
@Environment(EnvType.CLIENT)
public class BloodMoonRenderer {
    // Текущее "прорисованное" значение альфы (0.0 = нет эффекта, 1.0 = полностью красный).
    private static float bloodMoonAlpha = 0.0f;

    // Целевое значение альфы, к которому мы плавно двигаемся.
    private static float targetAlpha = 0.0f;

    /**
     * Регистрируемся в HudRenderCallback. Вызывается при каждом кадре с параметром DrawContext.
     */
    public static void init() {
        HudRenderCallback.EVENT.register(BloodMoonRenderer::onHudRender);
    }

    /**
     * Основной метод отрисовки. Каждый кадр мы интерполируем (lerp) текущее значение к нужному,
     * а затем рисуем полупрозрачный красный прямоугольник на весь экран.
     */
    private static void onHudRender(DrawContext drawContext, float tickDelta) {
        // Плавная интерполяция: за ~два десятка кадров bloodMoonAlpha "доедет" до targetAlpha
        // 0.05f -> чем выше, тем быстрее переход.
        bloodMoonAlpha = MathHelper.lerp(0.005f, bloodMoonAlpha, targetAlpha);

        // Если эффект почти нулевой, не рисуем (экономим).

        int screenWidth = drawContext.getScaledWindowWidth();
        int screenHeight = drawContext.getScaledWindowHeight();

        // Формируем цвет ARGB (alpha << 24 | red << 16 | green << 8 | blue)
        // Для красного: R=255, G=0, B=0 -> 0x00FF0000.
        // Альфа (0..255) получаем из bloodMoonAlpha (0..1).
        int alpha = (int) (bloodMoonAlpha * 255.0f) << 24;  // в старших 8 бит
        int color = alpha | 0x00FF0000; // 0x00FF0000 = чистый красный

        // Рисуем заливку на весь экран.
        // Поскольку это HudRenderCallback, идёт поверх HUD,
        // но благодаря полупрозрачности HUD остаётся различимым.
        drawContext.fill(0, 0, screenWidth, screenHeight, color);
    }

    /**
     * Вызываем из CodeZeroClient, когда сервер сообщает:
     * "включить" (true) или "выключить" (false) «кровавую ночь».
     */
    public static void setBloodMoonActive(boolean active) {
        // Например, хотим максимум 50% красноты. Можете взять 0.3f для менее яркого эффекта.
        targetAlpha = active ? 0.08f : 0.0f;
    }
}
