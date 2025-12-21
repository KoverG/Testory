package app.shell;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Objects;

public final class DrawerEngine {

    public enum Side { LEFT, RIGHT }

    public interface BoolFn {
        boolean get();
    }

    private final VBox panel;
    private final Pane overlay;
    private final Side side;

    private final Runnable updateGeometry;
    private final BoolFn anyDrawerOpen;

    private final TranslateTransition anim;

    // hooks (опционально)
    private final Runnable onOpenStart;
    private final Runnable onCloseStart;
    private final Runnable onStateChanged;

    private boolean open = false;

    public DrawerEngine(
            VBox panel,
            Pane overlay,
            Side side,
            Runnable updateGeometry,
            BoolFn anyDrawerOpen,
            Duration animDuration
    ) {
        this(panel, overlay, side, updateGeometry, anyDrawerOpen, animDuration, null, null, null);
    }

    public DrawerEngine(
            VBox panel,
            Pane overlay,
            Side side,
            Runnable updateGeometry,
            BoolFn anyDrawerOpen,
            Duration animDuration,
            Runnable onOpenStart,
            Runnable onCloseStart,
            Runnable onStateChanged
    ) {
        this.panel = Objects.requireNonNull(panel);
        this.overlay = Objects.requireNonNull(overlay);
        this.side = Objects.requireNonNull(side);

        this.updateGeometry = Objects.requireNonNull(updateGeometry);
        this.anyDrawerOpen = Objects.requireNonNull(anyDrawerOpen);

        this.onOpenStart = onOpenStart;
        this.onCloseStart = onCloseStart;
        this.onStateChanged = onStateChanged;

        Duration d = (animDuration != null) ? animDuration : Duration.millis(180);

        this.anim = new TranslateTransition(d, panel);
        this.anim.setInterpolator(Interpolator.EASE_BOTH);
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        if (open) return;

        open = true;

        updateGeometry.run();

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

            if (!anyDrawerOpen.get()) overlay.setVisible(false);

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

        if (!anyDrawerOpen.get()) overlay.setVisible(false);

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
