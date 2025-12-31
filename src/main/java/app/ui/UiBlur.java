package app.ui;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.effect.GaussianBlur;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class UiBlur {

    private final Duration anim;
    private final double radiusOn;

    private final GaussianBlur blurFx = new GaussianBlur(0.0);

    private Timeline tl;
    private boolean active;

    private final List<Node> targets = new ArrayList<>();

    public UiBlur(Duration anim, double radiusOn) {
        this.anim = (anim == null) ? Duration.millis(180) : anim;
        this.radiusOn = radiusOn <= 0 ? 10.0 : radiusOn;
    }

    public boolean isActive() {
        return active;
    }

    public void setTargets(Node... nodes) {
        targets.clear();
        if (nodes == null) return;
        targets.addAll(Arrays.asList(nodes));
        syncTargetsEffect();
    }

    public void setTargets(Collection<? extends Node> nodes) {
        targets.clear();
        if (nodes == null) return;
        targets.addAll(nodes);
        syncTargetsEffect();
    }

    public void setActive(boolean value) {
        if (value == active) return;
        active = value;

        if (tl != null) tl.stop();

        double from = blurFx.getRadius();
        double to = active ? radiusOn : 0.0;

        if (active) {
            for (Node t : targets) {
                if (t != null) t.setEffect(blurFx);
            }
        }

        tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(blurFx.radiusProperty(), from, Interpolator.EASE_BOTH)
                ),
                new KeyFrame(anim,
                        new KeyValue(blurFx.radiusProperty(), to, Interpolator.EASE_BOTH)
                )
        );

        tl.setOnFinished(e -> {
            if (!active) {
                for (Node t : targets) {
                    if (t != null) t.setEffect(null);
                }
            }
        });

        tl.play();
    }

    public void dispose() {
        if (tl != null) tl.stop();
        tl = null;
        active = false;
        for (Node t : targets) {
            if (t != null) t.setEffect(null);
        }
        targets.clear();
    }

    private void syncTargetsEffect() {
        if (!active) return;
        for (Node t : targets) {
            if (t != null) t.setEffect(blurFx);
        }
    }
}
