// FILE: src/main/java/app/domain/testcases/ui/SmoothScrollSupport.java
package app.domain.testcases.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

public final class SmoothScrollSupport {

    private Timeline tl;
    private double targetV = 0.0;

    public void install(ScrollPane sp) {
        if (sp == null) return;

        sp.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isInertia()) return;
            if (Math.abs(e.getDeltaY()) < 0.0001) return;

            e.consume();

            final double k = 0.0046;
            double deltaV = -e.getDeltaY() * k;

            double base = sp.getVvalue();
            if (tl != null && tl.getStatus() == Timeline.Status.RUNNING) {
                base = sp.getVvalue();
            } else {
                targetV = base;
            }

            targetV = clamp01(targetV + deltaV);
            animateScrollTo(sp, targetV);
        });
    }

    private void animateScrollTo(ScrollPane sp, double v) {
        if (tl != null) tl.stop();

        tl = new Timeline(
                new KeyFrame(Duration.millis(120),
                        new KeyValue(sp.vvalueProperty(), v, Interpolator.EASE_OUT)
                )
        );
        tl.play();
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
