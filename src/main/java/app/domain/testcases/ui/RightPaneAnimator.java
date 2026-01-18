// FILE: src/main/java/app/domain/testcases/ui/RightPaneAnimator.java
package app.domain.testcases.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

public final class RightPaneAnimator {

    public static final double DEFAULT_DX = 22.0;
    public static final double DEFAULT_MS = 220.0;

    // "мягкая замена" A→B
    private static final double PULSE_DX = 8.0;
    private static final double PULSE_TO_OPACITY = 0.65;
    private static final double PULSE_MS = 160.0; // чуть быстрее чем show/hide

    private final Node node;

    private double dx = DEFAULT_DX;
    private double ms = DEFAULT_MS;

    private Animation running;

    public RightPaneAnimator(Node node) {
        this.node = node;
    }

    public void setDx(double dx) {
        this.dx = dx;
    }

    public void setMs(double ms) {
        this.ms = ms;
    }

    public void stop() {
        if (running != null) {
            try { running.stop(); } catch (Exception ignore) {}
            running = null;
        }
    }

    public void show(Runnable beforePlay, Runnable onFinished) {
        if (node == null) return;

        stop();

        if (beforePlay != null) beforePlay.run();

        node.setOpacity(0.0);
        node.setTranslateX(-dx);

        FadeTransition fade = new FadeTransition(Duration.millis(ms), node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(ms), node);
        slide.setFromX(-dx);
        slide.setToX(0.0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.setOnFinished(e -> {
            node.setOpacity(1.0);
            node.setTranslateX(0.0);
            running = null;
            if (onFinished != null) onFinished.run();
        });

        running = pt;
        pt.play();
    }

    public void hide(Runnable beforePlay, Runnable onFinished) {
        if (node == null) return;

        stop();

        if (beforePlay != null) beforePlay.run();

        double fromOpacity = node.getOpacity();
        double fromX = node.getTranslateX();

        FadeTransition fade = new FadeTransition(Duration.millis(ms), node);
        fade.setFromValue(fromOpacity);
        fade.setToValue(0.0);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(ms), node);
        slide.setFromX(fromX);
        slide.setToX(-dx);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.setOnFinished(e -> {
            node.setOpacity(0.0);
            node.setTranslateX(0.0); // важно: чтобы следующее show стартовало предсказуемо
            running = null;
            if (onFinished != null) onFinished.run();
        });

        running = pt;
        pt.play();
    }

    // A→B: контент уже обновлён, мы просто делаем "премиальный" dip.
    public void pulseReplace() {
        if (node == null) return;

        stop();

        double fromOpacity = clamp01(node.getOpacity());
        double fromX = node.getTranslateX();

        Duration half = Duration.millis(PULSE_MS / 2.0);

        FadeTransition fadeOut = new FadeTransition(half, node);
        fadeOut.setFromValue(fromOpacity);
        fadeOut.setToValue(PULSE_TO_OPACITY);
        fadeOut.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slideOut = new TranslateTransition(half, node);
        slideOut.setFromX(fromX);
        slideOut.setToX(-PULSE_DX);
        slideOut.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition out = new ParallelTransition(fadeOut, slideOut);

        FadeTransition fadeIn = new FadeTransition(half, node);
        fadeIn.setFromValue(PULSE_TO_OPACITY);
        fadeIn.setToValue(1.0);
        fadeIn.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slideIn = new TranslateTransition(half, node);
        slideIn.setFromX(-PULSE_DX);
        slideIn.setToX(0.0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition in = new ParallelTransition(fadeIn, slideIn);

        SequentialTransition seq = new SequentialTransition(out, in);
        seq.setOnFinished(e -> {
            node.setOpacity(1.0);
            node.setTranslateX(0.0);
            running = null;
        });

        running = seq;
        seq.play();
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
