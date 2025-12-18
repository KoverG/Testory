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

    private boolean open = false;

    public DrawerEngine(
            VBox panel,
            Pane overlay,
            Side side,
            Runnable updateGeometry,
            BoolFn anyDrawerOpen,
            Duration animDuration
    ) {
        this.panel = Objects.requireNonNull(panel);
        this.overlay = Objects.requireNonNull(overlay);
        this.side = Objects.requireNonNull(side);

        this.updateGeometry = Objects.requireNonNull(updateGeometry);
        this.anyDrawerOpen = Objects.requireNonNull(anyDrawerOpen);

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

        anim.stop();
        anim.setFromX(panel.getTranslateX());
        anim.setToX(0);
        anim.setOnFinished(null);
        anim.play();
    }

    public void close() {
        if (!open) return;

        open = false;

        anim.stop();
        anim.setFromX(panel.getTranslateX());
        anim.setToX(closedX());
        anim.setOnFinished(ev -> {
            panel.setVisible(false);
            anim.setOnFinished(null);

            if (!anyDrawerOpen.get()) overlay.setVisible(false);

            panel.setTranslateX(closedX());
        });
        anim.play();
    }

    public void closeImmediate() {
        if (!open) return;

        open = false;

        anim.stop();
        panel.setVisible(false);
        panel.setTranslateX(closedX());

        if (!anyDrawerOpen.get()) overlay.setVisible(false);
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
