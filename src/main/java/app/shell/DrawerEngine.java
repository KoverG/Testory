package app.shell;

import javafx.animation.TranslateTransition;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.BooleanSupplier;

public final class DrawerEngine {

    public enum Side { LEFT, RIGHT }

    private final VBox panel;
    private final Pane overlay;
    private final Side side;

    private final Runnable updateOverlayGeometry;
    private final BooleanSupplier anyDrawerOpen;

    private final TranslateTransition anim;

    private final Runnable onOpenStart;
    private final Runnable onCloseStart;
    private final Runnable onStateChanged;

    private boolean open = false;

    public DrawerEngine(
            VBox panel,
            Pane overlay,
            Side side,
            Runnable updateOverlayGeometry,
            BooleanSupplier anyDrawerOpen,
            Duration animDuration,
            Runnable onOpenStart,
            Runnable onCloseStart,
            Runnable onStateChanged
    ) {
        this.panel = panel;
        this.overlay = overlay;
        this.side = side;

        this.updateOverlayGeometry = updateOverlayGeometry;
        this.anyDrawerOpen = anyDrawerOpen;

        this.onOpenStart = onOpenStart;
        this.onCloseStart = onCloseStart;
        this.onStateChanged = onStateChanged;

        this.anim = new TranslateTransition(animDuration, panel);
        this.anim.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        if (open) return;

        open = true;

        if (updateOverlayGeometry != null) updateOverlayGeometry.run();

        overlay.setVisible(true);
        panel.setVisible(true);
        panel.setTranslateX(closedX());

        if (onOpenStart != null) onOpenStart.run();
        if (onStateChanged != null) onStateChanged.run();

        anim.stop();
        anim.setFromX(panel.getTranslateX());
        anim.setToX(0);
        anim.setOnFinished(null);
        anim.play();
    }

    /**
     * Важно для i18n-reload: восстановить drawer без анимации,
     * чтобы визуально он не "закрывался и открывался заново".
     */
    public void openImmediate() {
        if (open) return;

        open = true;

        if (updateOverlayGeometry != null) updateOverlayGeometry.run();

        overlay.setVisible(true);
        panel.setVisible(true);

        anim.stop();
        panel.setTranslateX(0);

        if (onOpenStart != null) onOpenStart.run();
        if (onStateChanged != null) onStateChanged.run();
    }

    public void close() {
        if (!open) return;

        open = false;

        if (onCloseStart != null) onCloseStart.run();
        if (onStateChanged != null) onStateChanged.run();

        anim.stop();
        anim.setFromX(panel.getTranslateX());
        anim.setToX(closedX());
        anim.setOnFinished(ev -> {
            panel.setVisible(false);
            anim.setOnFinished(null);

            if (!anyDrawerOpen.getAsBoolean()) overlay.setVisible(false);

            panel.setTranslateX(closedX());

            if (onStateChanged != null) onStateChanged.run();
        });
        anim.play();
    }

    public void closeImmediate() {
        if (!open) return;

        open = false;

        if (onCloseStart != null) onCloseStart.run();
        if (onStateChanged != null) onStateChanged.run();

        anim.stop();
        panel.setVisible(false);
        panel.setTranslateX(closedX());

        if (!anyDrawerOpen.getAsBoolean()) overlay.setVisible(false);

        if (onStateChanged != null) onStateChanged.run();
    }

    public void snapClosed() {
        open = false;
        anim.stop();
        panel.setVisible(false);
        panel.setTranslateX(closedX());
    }

    private double closedX() {
        double w = drawerWidth();
        return (side == Side.LEFT) ? -w : +w;
    }

    private double drawerWidth() {
        double w = panel.getPrefWidth();
        if (w > 0) return w;
        return panel.getWidth();
    }
}
