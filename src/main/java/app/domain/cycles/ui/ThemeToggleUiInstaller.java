// FILE: src/main/java/app/domain/cycles/ui/ThemeToggleUiInstaller.java
package app.domain.cycles.ui;

import app.domain.cycles.ui.left.LeftMode;
import app.domain.cycles.ui.left.LeftPaneCoordinator;
import javafx.beans.property.BooleanProperty;
import javafx.scene.Node;
import javafx.scene.shape.SVGPath;

import java.lang.reflect.Method;

/**
 * Инсталлятор для toggle в экране Cycles.
 *
 * Теперь:
 * - ставит SVG-иконки (cycle/case)
 * - и ВЕСЬ код переключения списка живёт здесь:
 *   selected=false => Cycles list
 *   selected=true  => Cases picker
 *
 * Реализация остаётся через reflection, чтобы не создавать конфликтов с API ToggleSwitch.
 */
public final class ThemeToggleUiInstaller {

    private static final String PROP_SYNC_GUARD = "__cy_tg_list_sync";

    private ThemeToggleUiInstaller() {}

    public static void install(Object toggleSwitchInstance) {
        install(toggleSwitchInstance, null);
    }

    public static void install(Object toggleSwitchInstance, LeftPaneCoordinator left) {
        if (toggleSwitchInstance == null) return;

        // ✅ Иконки: OFF = cycle (default cycles list), ON = case (cases picker)
        Node kase = tryCreateSvgIcon("case.svg", 12);
        Node cycle  = tryCreateSvgIcon("cycle.svg", 12);

        if (kase != null) {
            tryInvoke(toggleSwitchInstance, "setGraphicOff", Node.class, kase);
            tryInvoke(toggleSwitchInstance, "setOffGraphic", Node.class, kase);
        }

        if (cycle != null) {
            tryInvoke(toggleSwitchInstance, "setGraphicOn", Node.class, cycle);
            tryInvoke(toggleSwitchInstance, "setOnGraphic", Node.class, cycle);
        }

        // ====== list switching binding ======
        if (left == null) return;

        // 1) init selected from current mode
        syncToggleFromMode(toggleSwitchInstance, left.mode());

        // 2) user toggles => change mode
        BooleanProperty selected = tryGetSelectedProperty(toggleSwitchInstance);
        if (selected != null) {
            selected.addListener((obs, oldV, newV) -> {
                if (isSyncGuard(toggleSwitchInstance)) return;

                boolean on = newV != null && newV;
                LeftMode target = on ? LeftMode.CASES_PICKER : LeftMode.CYCLES_LIST;
                left.setMode(target);
            });
        }

        // 3) mode changes from other places => sync toggle state
        left.setOnModeChanged(() -> syncToggleFromMode(toggleSwitchInstance, left.mode()));
    }

    private static void syncToggleFromMode(Object toggleSwitchInstance, LeftMode mode) {
        boolean wantOn = mode == LeftMode.CASES_PICKER;

        setSyncGuard(toggleSwitchInstance, true);
        try {
            // preferred: setSelected(boolean)
            tryInvoke(toggleSwitchInstance, "setSelected", boolean.class, wantOn);

            // fallback: selectedProperty().set(...)
            BooleanProperty p = tryGetSelectedProperty(toggleSwitchInstance);
            if (p != null) p.set(wantOn);
        } finally {
            setSyncGuard(toggleSwitchInstance, false);
        }
    }

    private static boolean isSyncGuard(Object toggleSwitchInstance) {
        if (toggleSwitchInstance instanceof Node n) {
            Object v = n.getProperties().get(PROP_SYNC_GUARD);
            return Boolean.TRUE.equals(v);
        }
        return false;
    }

    private static void setSyncGuard(Object toggleSwitchInstance, boolean v) {
        if (toggleSwitchInstance instanceof Node n) {
            if (v) n.getProperties().put(PROP_SYNC_GUARD, Boolean.TRUE);
            else n.getProperties().remove(PROP_SYNC_GUARD);
        }
    }

    private static BooleanProperty tryGetSelectedProperty(Object toggleSwitchInstance) {
        try {
            Method m = toggleSwitchInstance.getClass().getMethod("selectedProperty");
            Object res = m.invoke(toggleSwitchInstance);
            if (res instanceof BooleanProperty bp) return bp;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Пытается создать SVG-Node через app.ui.UiSvg.createSvg(String, double).
     * Если утилита/метод отличается — возвращает null (UI останется как у ToggleSwitch по умолчанию).
     */
    private static Node tryCreateSvgIcon(String fileName, double sizePx) {
        try {
            Class<?> uiSvg = Class.forName("app.ui.UiSvg");

            Method m = uiSvg.getMethod("createSvg", String.class, double.class);
            Object res = m.invoke(null, fileName, sizePx);

            if (res instanceof Node node) {
                if (node instanceof SVGPath p) {
                    p.getStyleClass().add("toggle-icon");
                }
                return node;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void tryInvoke(Object target, String methodName, Class<?> paramType, Object arg) {
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, arg);
        } catch (Throwable ignored) {
        }
    }

    private static void tryInvoke(Object target, String methodName, Class<?> paramType, boolean arg) {
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, arg);
        } catch (Throwable ignored) {
        }
    }
}
