package app.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.Skin;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import javafx.util.Duration;

import java.lang.reflect.Method;

/**
 * Global ComboBox styling helper.
 *
 * Responsibilities:
 * - Adds a stable style hook to the popup ListView (styleClass: "app-combo-popup")
 * - Plays a smooth show animation for the popup content (layout-safe)
 * - Fixes JavaFX first-show sizing glitch (selective one-time hide->show if needed)
 *
 * NOTE:
 * - All visual styling for the popup is handled via CSS (.list-view.app-combo-popup).
 * - popupOpacity is kept for backward compatibility (stored but ignored visually).
 */
public final class UiComboBox {

    private static final String POPUP_STYLE_CLASS = "app-combo-popup";

    private static final PseudoClass PC_COMBO_FOCUSED = PseudoClass.getPseudoClass("combo-focused");
    private static final PseudoClass PC_POPUP_UP = PseudoClass.getPseudoClass("popup-up");

    // kept for backward compatibility (value is stored but ignored visually)
    private static final String PROP_POPUP_OPACITY = "appComboPopupOpacity";

    private static final String PROP_FOCUS_SYNC_LISTENER = "appComboFocusSyncListener";

    // one-time first-show fix flags
    private static final String PROP_FIRST_SHOW_FIXED = "appComboFirstShowFixed";
    private static final String PROP_RESHOWING = "appComboReshowing";

    // popup Y lock (to prevent JavaFX auto-reposition from undoing our flip-up)
    private static final String PROP_POPUP_Y_LOCK_LISTENER = "appComboPopupYLockListener";
    private static final String PROP_POPUP_Y_LOCK_GUARD = "appComboPopupYLockGuard";
    private static final String PROP_POPUP_Y_LOCK_TARGET = "appComboPopupYLockTarget";
    private static final String PROP_POPUP_Y_LOCK_WINDOW = "appComboPopupYLockWindow";

    private static final Duration SHOW_ANIM = Duration.millis(140);

    // If you still see a tiny seam after CSS padding fix, keep this 1px overlap.
    // Set to 0.0 if you really want no overlap.
    private static final double SEAM_COMP_PHYSICAL_PX = 1.0;

    private UiComboBox() {}

    public static void install(ComboBox<?> cb) {
        install(cb, 0.92);
    }

    /**
     * @param popupOpacity 0..1 (kept for backward compatibility). Ignored now: popup is SOLID by design.
     */
    public static void install(ComboBox<?> cb, double popupOpacity) {
        if (cb == null) return;

        setPopupOpacity(cb, popupOpacity);

        cb.showingProperty().addListener((obs, was, is) -> {
            if (!is) {
                detachFocusSync(cb);
                unlockPopupY(cb);

                try {
                    cb.pseudoClassStateChanged(PC_POPUP_UP, false);
                } catch (Exception ignore) {}
                return;
            }
            Platform.runLater(() -> preparePopup(cb));
        });
    }

    /**
     * Kept for backward compatibility. Value is stored but not used visually.
     */
    public static void setPopupOpacity(ComboBox<?> cb, double popupOpacity) {
        if (cb == null) return;
        cb.getProperties().put(PROP_POPUP_OPACITY, clamp01(popupOpacity));
    }

    // ===================== INTERNAL =====================

    private static void preparePopup(ComboBox<?> cb) {
        if (cb == null) return;

        Node content = tryGetPopupContent(cb);
        if (content == null) return;

        ListView<?> lv = findListView(content);
        if (lv == null) return;

        if (!lv.getStyleClass().contains(POPUP_STYLE_CLASS)) {
            lv.getStyleClass().add(POPUP_STYLE_CLASS);
        }

        attachFocusSync(cb, lv);

        Platform.runLater(() -> {
            try {
                content.applyCss();
                if (content instanceof Parent p) {
                    p.requestLayout();
                    p.layout();
                }

                lv.applyCss();
                lv.requestLayout();
                lv.layout();
            } catch (Exception ignore) {}

            Platform.runLater(() -> {
                adjustPopupFlipToRootBounds(cb, lv);
                Platform.runLater(() -> adjustPopupFlipToRootBounds(cb, lv));
            });

            if (needsFirstShowFix(cb, lv)) {
                runFirstShowFix(cb);
                return;
            }

            if (isReshowing(cb)) return;

            Platform.runLater(() -> playShowAnimationSafe(lv));
        });
    }

    /**
     * RULE:
     * - If popup fits below inside Scene.getRoot() bounds -> DO NOTHING (keep JavaFX default positioning; no gaps).
     * - Only if it doesn't fit below -> flip UP and LOCK popup Y so JavaFX can't undo it.
     *
     * For UP:
     * - Align using ListView bottom minus its bottom inset (removes "empty row" padding effect).
     */
    private static void adjustPopupFlipToRootBounds(ComboBox<?> cb, ListView<?> lv) {
        if (cb == null || lv == null) return;

        try {
            if (cb.getScene() == null) return;
            Parent root = cb.getScene().getRoot();
            if (root == null) return;

            Bounds rootScreen = root.localToScreen(root.getLayoutBounds());
            if (rootScreen == null) return;

            if (lv.getScene() == null) return;
            Window win = lv.getScene().getWindow();
            if (win == null) return;

            Point2D cbTop = cb.localToScreen(0, 0);
            Point2D cbBottom = cb.localToScreen(0, cb.getHeight());
            if (cbTop == null || cbBottom == null) return;

            Bounds lvScreen = lv.localToScreen(lv.getBoundsInLocal());
            if (lvScreen == null) return;

            double listH = lvScreen.getHeight();
            if (listH <= 1) return;

            double spaceDown = rootScreen.getMaxY() - cbBottom.getY();
            double spaceUp = cbTop.getY() - rootScreen.getMinY();

            boolean flipUp = listH > spaceDown && spaceUp > spaceDown;

            lv.pseudoClassStateChanged(PC_POPUP_UP, flipUp);
            cb.pseudoClassStateChanged(PC_POPUP_UP, flipUp);

            if (!flipUp) {
                unlockPopupY(cb);
                return;
            }

            double bottomInset = 0.0;
            try {
                bottomInset = lv.snappedBottomInset();
            } catch (Exception ignore) {}

            double targetListBottom = lvScreen.getMaxY() - bottomInset;
            double delta = targetListBottom - cbTop.getY();

            double seam = 0.0;
            if (win instanceof PopupWindow pw) {
                double scaleY = pw.getOutputScaleY();
                if (!(scaleY > 0)) scaleY = 1.0;
                seam = SEAM_COMP_PHYSICAL_PX / scaleY;
            }

            double desiredWinY = win.getY() - delta - seam;

            double winH = win.getHeight();
            if (winH <= 1) return;

            double minY = rootScreen.getMinY();
            double maxY = rootScreen.getMaxY() - winH;
            double finalY = clamp(desiredWinY, minY, maxY);

            if (win instanceof PopupWindow pw) {
                lockPopupY(cb, pw, finalY);
                pw.setY(finalY);
            } else {
                win.setY(finalY);
            }

        } catch (Exception ignore) {}
    }

    // ===================== POPUP Y LOCK =====================

    /**
     * IMPORTANT FIX:
     * Reinstall the lock listener for the CURRENT PopupWindow each time,
     * because ComboBox popup window instance can change between shows.
     */
    @SuppressWarnings("unchecked")
    private static void lockPopupY(ComboBox<?> cb, PopupWindow pw, double targetY) {
        if (cb == null || pw == null) return;

        cb.getProperties().put(PROP_POPUP_Y_LOCK_TARGET, targetY);

        // If we have a previous listener/window, detach first (avoid binding to stale window)
        Object oldListenerObj = cb.getProperties().get(PROP_POPUP_Y_LOCK_LISTENER);
        Object oldWinObj = cb.getProperties().get(PROP_POPUP_Y_LOCK_WINDOW);

        if (oldListenerObj instanceof ChangeListener<?> oldL && oldWinObj instanceof PopupWindow oldPw) {
            try {
                oldPw.yProperty().removeListener((ChangeListener<Number>) oldL);
            } catch (Exception ignore) {}
        }

        ChangeListener<Number> l = (obs, oldV, newV) -> {
            Object g = cb.getProperties().get(PROP_POPUP_Y_LOCK_GUARD);
            if (g instanceof Boolean b && b) return;

            Object t = cb.getProperties().get(PROP_POPUP_Y_LOCK_TARGET);
            if (!(t instanceof Double target)) return;

            double y = pw.getY();
            if (Math.abs(y - target) < 0.5) return;

            cb.getProperties().put(PROP_POPUP_Y_LOCK_GUARD, Boolean.TRUE);
            try {
                Platform.runLater(() -> {
                    try {
                        pw.setY(target);
                    } catch (Exception ignore) {
                    } finally {
                        cb.getProperties().put(PROP_POPUP_Y_LOCK_GUARD, Boolean.FALSE);
                    }
                });
            } catch (Exception ignore) {
                cb.getProperties().put(PROP_POPUP_Y_LOCK_GUARD, Boolean.FALSE);
            }
        };

        cb.getProperties().put(PROP_POPUP_Y_LOCK_LISTENER, l);
        cb.getProperties().put(PROP_POPUP_Y_LOCK_WINDOW, pw);
        pw.yProperty().addListener(l);
    }

    @SuppressWarnings("unchecked")
    private static void unlockPopupY(ComboBox<?> cb) {
        if (cb == null) return;

        Object listenerObj = cb.getProperties().remove(PROP_POPUP_Y_LOCK_LISTENER);
        Object winObj = cb.getProperties().remove(PROP_POPUP_Y_LOCK_WINDOW);

        cb.getProperties().remove(PROP_POPUP_Y_LOCK_TARGET);
        cb.getProperties().remove(PROP_POPUP_Y_LOCK_GUARD);

        if (!(listenerObj instanceof ChangeListener<?> l)) return;

        try {
            if (winObj instanceof PopupWindow pw) {
                pw.yProperty().removeListener((ChangeListener<Number>) l);
            }
        } catch (Exception ignore) {}
    }

    // ===================== FIRST SHOW FIX =====================

    private static boolean needsFirstShowFix(ComboBox<?> cb, ListView<?> lv) {
        if (cb == null || lv == null) return false;
        if (isReshowing(cb)) return false;

        Object done = cb.getProperties().get(PROP_FIRST_SHOW_FIXED);
        boolean alreadyFixed = (done instanceof Boolean b && b);
        if (alreadyFixed) return false;

        int items = 0;
        try {
            if (lv.getItems() != null) items = lv.getItems().size();
        } catch (Exception ignore) {
            items = 0;
        }

        if (items <= 0) return false;
        if (items > 12) return false;

        double h = lv.getHeight();
        double pref = lv.prefHeight(-1);

        if (h <= 1) return false;
        if (pref <= 1) return false;

        return h + 6.0 < pref;
    }

    private static boolean isReshowing(ComboBox<?> cb) {
        if (cb == null) return false;
        Object v = cb.getProperties().get(PROP_RESHOWING);
        return (v instanceof Boolean b && b);
    }

    private static void runFirstShowFix(ComboBox<?> cb) {
        if (cb == null) return;

        cb.getProperties().put(PROP_FIRST_SHOW_FIXED, Boolean.TRUE);
        cb.getProperties().put(PROP_RESHOWING, Boolean.TRUE);

        Platform.runLater(() -> {
            try {
                cb.hide();
            } catch (Exception ignore) {}

            Platform.runLater(() -> {
                try {
                    cb.show();
                } catch (Exception ignore) {
                } finally {
                    cb.getProperties().put(PROP_RESHOWING, Boolean.FALSE);
                }
            });
        });
    }

    private static void playShowAnimationSafe(ListView<?> lv) {
        if (lv == null) return;

        lv.setOpacity(0.0);

        boolean popupUp = lv.getPseudoClassStates().contains(PC_POPUP_UP);
        lv.setTranslateY(popupUp ? 2.0 : -2.0);

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(lv.opacityProperty(), 0.0),
                        new KeyValue(lv.translateYProperty(), popupUp ? 2.0 : -2.0)
                ),
                new KeyFrame(SHOW_ANIM,
                        new KeyValue(lv.opacityProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(lv.translateYProperty(), 0.0, Interpolator.EASE_BOTH)
                )
        );

        tl.setCycleCount(1);
        tl.setAutoReverse(false);
        tl.play();
    }

    // ===================== STATE SYNC (FOCUS -> POPUP) =====================

    @SuppressWarnings("unchecked")
    private static void attachFocusSync(ComboBox<?> cb, ListView<?> lv) {
        if (cb == null || lv == null) return;

        detachFocusSync(cb);

        lv.pseudoClassStateChanged(PC_COMBO_FOCUSED, cb.isFocused());

        ChangeListener<Boolean> l = (obs, was, is) -> {
            try {
                lv.pseudoClassStateChanged(PC_COMBO_FOCUSED, is);
            } catch (Exception ignore) {}
        };

        cb.getProperties().put(PROP_FOCUS_SYNC_LISTENER, l);
        cb.focusedProperty().addListener(l);
    }

    @SuppressWarnings("unchecked")
    private static void detachFocusSync(ComboBox<?> cb) {
        if (cb == null) return;

        Object o = cb.getProperties().remove(PROP_FOCUS_SYNC_LISTENER);
        if (o instanceof ChangeListener<?> l) {
            try {
                cb.focusedProperty().removeListener((ChangeListener<Boolean>) l);
            } catch (Exception ignore) {}
        }
    }

    // ===================== POPUP ACCESS (REFLECTION) =====================

    private static Node tryGetPopupContent(ComboBox<?> cb) {
        if (cb == null) return null;

        try {
            Skin<?> skin = cb.getSkin();
            if (skin != null) {
                Node n = tryInvokeGetPopupContent(skin);
                if (n != null) return n;
            }
        } catch (Exception ignore) {}

        try {
            cb.applyCss();
            cb.layout();
            Skin<?> skin = cb.getSkin();
            if (skin != null) {
                Node n = tryInvokeGetPopupContent(skin);
                if (n != null) return n;
            }
        } catch (Exception ignore) {}

        return null;
    }

    private static Node tryInvokeGetPopupContent(Object skin) {
        if (skin == null) return null;
        try {
            Method m = skin.getClass().getDeclaredMethod("getPopupContent");
            m.setAccessible(true);
            Object o = m.invoke(skin);
            if (o instanceof Node n) return n;
        } catch (Throwable ignore) {}
        return null;
    }

    private static ListView<?> findListView(Node root) {
        if (root == null) return null;

        if (root instanceof ListView<?> lv) return lv;

        if (root instanceof Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                ListView<?> out = findListView(c);
                if (out != null) return out;
            }
        }
        return null;
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        if (max < min) return min;
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
