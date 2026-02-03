package app.domain.cycles;

import javafx.scene.Node;
import javafx.scene.shape.SVGPath;

import java.lang.reflect.Method;

/**
 * UI-only инсталлятор для theme toggle.
 *
 * Важно:
 * - не навешивает логику (никаких listeners/selected и т.п.)
 * - старается использовать уже существующие утилиты проекта (UiSvg + ToggleSwitch API),
 *   но делает это через reflection, чтобы не создавать конфликты по сигнатурам/названиям методов.
 */
public final class ThemeToggleUiInstaller {

    private ThemeToggleUiInstaller() {}

    public static void install(Object toggleSwitchInstance) {
        if (toggleSwitchInstance == null) return;

        Node sun = tryCreateSvgIcon("sun.svg", 12);
        Node moon = tryCreateSvgIcon("moon.svg", 12);

        // Пробуем распространённые API названия без жесткой зависимости
        // (если метод не найден — тихо пропускаем).
        if (sun != null) {
            tryInvoke(toggleSwitchInstance, "setGraphicOn", Node.class, sun);
            tryInvoke(toggleSwitchInstance, "setOnGraphic", Node.class, sun);
        }

        if (moon != null) {
            tryInvoke(toggleSwitchInstance, "setGraphicOff", Node.class, moon);
            tryInvoke(toggleSwitchInstance, "setOffGraphic", Node.class, moon);
        }
    }

    /**
     * Пытается создать SVG-Node через app.ui.UiSvg.createSvg(String, double).
     * Если утилита/метод отличается — возвращает null (UI останется как у ToggleSwitch по умолчанию).
     */
    private static Node tryCreateSvgIcon(String fileName, double sizePx) {
        try {
            Class<?> uiSvg = Class.forName("app.ui.UiSvg");

            // Самый ожидаемый метод: createSvg(String, double)
            Method m = uiSvg.getMethod("createSvg", String.class, double.class);
            Object res = m.invoke(null, fileName, sizePx);

            if (res instanceof Node node) {
                // Стиль как у toggle-иконок (если в проекте так заведено)
                if (node instanceof SVGPath p) {
                    p.getStyleClass().add("toggle-icon");
                }
                return node;
            }
        } catch (Throwable ignored) {
            // намеренно тихо: это UI-only, не ломаем запуск из-за несовпадения API
        }
        return null;
    }

    private static void tryInvoke(Object target, String methodName, Class<?> paramType, Object arg) {
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, arg);
        } catch (Throwable ignored) {
            // тихо — см. комментарий выше
        }
    }
}
