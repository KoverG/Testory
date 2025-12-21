package app.ui;

import javafx.animation.*;
import javafx.beans.property.*;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.effect.BlendMode;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class ToggleSwitch extends StackPane {
    private boolean animated = true;

    private static final double WIDTH = 57;
    private static final double HEIGHT = 27;
    private static final double PADDING = 2;
    private static final double KNOB = HEIGHT - 2 * PADDING;        // 23
    private static final double SHIFT = WIDTH - 2 * PADDING - KNOB;  // ~30

    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    // Иконки состояния (показывается только одна)
    private final ObjectProperty<Node> graphicOn  = new SimpleObjectProperty<>(null);
    private final ObjectProperty<Node> graphicOff = new SimpleObjectProperty<>(null);

    private final Region track = new Region();
    private final Region thumb = new Region();

    // контейнер с клипом
    private final StackPane content = new StackPane();

    // слоты по краям
    private final StackPane iconLeft  = new StackPane();
    private final StackPane iconRight = new StackPane();

    // эффекты
    private final Circle ripple = new Circle();
    private final Region glint  = new Region();

    // тюнинг отскока
    private static final double OVERSHOOT_PX  = 12;
    private static final double UNDERSHOOT_PX = 6;

    private static final double SCALE_OVERSHOOT_X = 1.18;
    private static final double SCALE_OVERSHOOT_Y = 0.86;
    private static final double SCALE_UNDER_X     = 0.94;
    private static final double SCALE_UNDER_Y     = 1.06;

    private static final Duration T1 = Duration.millis(200);
    private static final Duration T2 = Duration.millis(380);
    private static final Duration T3 = Duration.millis(520);

    private Timeline toggleTl, rippleTl, glintTl;

    private static final PseudoClass PC_SELECTED = PseudoClass.getPseudoClass("selected");

    public ToggleSwitch() {
        getStyleClass().add("toggle-switch");
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
        setFocusTraversable(true);

        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        setPadding(Insets.EMPTY);

        // дорожка
        track.getStyleClass().add("track");
        track.setPrefSize(WIDTH, HEIGHT);
        track.setMinSize(WIDTH, HEIGHT);
        track.setMaxSize(WIDTH, HEIGHT);

        // шарик
        thumb.getStyleClass().add("thumb");
        thumb.setPrefSize(KNOB, KNOB);
        thumb.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        thumb.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(thumb, Pos.CENTER_LEFT);
        StackPane.setMargin(thumb, new Insets(0, 0, 0, PADDING));

        // слоты иконок (фиксировано по краям)
        iconLeft.setMouseTransparent(true);
        iconRight.setMouseTransparent(true);

        StackPane.setAlignment(iconLeft, Pos.CENTER_LEFT);
        StackPane.setAlignment(iconRight, Pos.CENTER_RIGHT);

        StackPane.setMargin(iconLeft, new Insets(0, 26, 0, 0));
        StackPane.setMargin(iconRight, new Insets(0, 0, 0, 26));

        // эффекты
        ripple.getStyleClass().add("toggle-ripple");
        ripple.setManaged(false);
        ripple.setOpacity(0);

        glint.getStyleClass().add("glint");
        glint.setManaged(false);
        glint.setOpacity(0);
        glint.setRotate(20);
        glint.setPrefSize(HEIGHT * 0.9, HEIGHT);

        content.setPrefSize(WIDTH, HEIGHT);
        content.setMinSize(WIDTH, HEIGHT);
        content.setMaxSize(WIDTH, HEIGHT);

        // иконки под thumb, но над track
        content.getChildren().addAll(track, ripple, glint, iconLeft, iconRight, thumb);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(content.widthProperty());
        clip.heightProperty().bind(content.heightProperty());
        clip.arcWidthProperty().bind(content.heightProperty());
        clip.arcHeightProperty().bind(content.heightProperty());
        content.setClip(clip);

        getChildren().add(content);

        // init
        applyState(false);

        setOnMouseEntered(e -> animateHover(true));
        setOnMouseExited(e -> animateHover(false));

        setOnMouseClicked(e -> setSelected(!isSelected()));
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                setSelected(!isSelected());
                e.consume();
            }
        });

        selected.addListener((obs, oldV, now) -> {
            if (animated) {
                animate(now);
                playRipple(now);
                sweepGlint();
                pseudoClassStateChanged(PC_SELECTED, now);
                updateStateIcon(now);
            } else {
                applyState(now);
            }
        });

        graphicOn.addListener((o, a, b) -> updateStateIcon(isSelected()));
        graphicOff.addListener((o, a, b) -> updateStateIcon(isSelected()));
    }

    // ===== Public API =====

    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean v) { selected.set(v); }

    public void setSelectedInstant(boolean v) { applyState(v); selected.set(v); }

    public boolean isAnimated() { return animated; }
    public void setAnimated(boolean v) { animated = v; }

    public void setGraphicOn(Node n)  { graphicOn.set(n); }
    public void setGraphicOff(Node n) { graphicOff.set(n); }

    // ===== Internals =====

    private void stopAllAnims() {
        if (toggleTl != null) toggleTl.stop();
        if (rippleTl != null) rippleTl.stop();
        if (glintTl  != null) glintTl.stop();
    }

    private void applyState(boolean on) {
        stopAllAnims();

        double target = on ? SHIFT : 0;

        thumb.setTranslateX(target);
        thumb.setScaleX(1);
        thumb.setScaleY(1);

        pseudoClassStateChanged(PC_SELECTED, on);

        ripple.setOpacity(0);
        glint.setOpacity(0);

        updateStateIcon(on);
    }

    // Требование: иконки сменяются при переключении
    // Светлая тема (on): SUN слева
    // Тёмная тема (off): MOON справа
    private void updateStateIcon(boolean on) {
        iconLeft.getChildren().clear();
        iconRight.getChildren().clear();

        if (on) {
            Node n = graphicOn.get();
            if (n != null) iconLeft.getChildren().add(n);
        } else {
            Node n = graphicOff.get();
            if (n != null) iconRight.getChildren().add(n);
        }
    }

    private void animateHover(boolean hover) {
        if (toggleTl != null && toggleTl.getStatus() == Animation.Status.RUNNING) return;
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(130),
                new KeyValue(thumb.scaleXProperty(), hover ? 1.06 : 1.0),
                new KeyValue(thumb.scaleYProperty(), hover ? 1.06 : 1.0)
        ));
        tl.play();
    }

    private void animate(boolean on) {
        stopAllAnims();

        double base = on ? SHIFT : 0;
        double overshoot = on ? base + OVERSHOOT_PX : base - OVERSHOOT_PX;
        double settle = on ? base - UNDERSHOOT_PX : base + UNDERSHOOT_PX;

        toggleTl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(thumb.translateXProperty(), thumb.getTranslateX(), Interpolator.EASE_OUT),
                        new KeyValue(thumb.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(thumb.scaleYProperty(), 1.0, Interpolator.EASE_OUT)
                ),
                new KeyFrame(T1,
                        new KeyValue(thumb.translateXProperty(), overshoot, Interpolator.EASE_OUT),
                        new KeyValue(thumb.scaleXProperty(), SCALE_OVERSHOOT_X, Interpolator.EASE_OUT),
                        new KeyValue(thumb.scaleYProperty(), SCALE_OVERSHOOT_Y, Interpolator.EASE_OUT)
                ),
                new KeyFrame(T2,
                        new KeyValue(thumb.translateXProperty(), settle, Interpolator.EASE_OUT),
                        new KeyValue(thumb.scaleXProperty(), SCALE_UNDER_X, Interpolator.EASE_OUT),
                        new KeyValue(thumb.scaleYProperty(), SCALE_UNDER_Y, Interpolator.EASE_OUT)
                ),
                new KeyFrame(T3,
                        new KeyValue(thumb.translateXProperty(), base, Interpolator.EASE_OUT),
                        new KeyValue(thumb.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(thumb.scaleYProperty(), 1.0, Interpolator.EASE_OUT)
                )
        );
        toggleTl.play();
    }

    private void playRipple(boolean on) {
        if (rippleTl != null) rippleTl.stop();

        ripple.setOpacity(0.0);
        ripple.setRadius(0);
        ripple.setCenterX(on ? WIDTH - HEIGHT / 2.0 : HEIGHT / 2.0);
        ripple.setCenterY(HEIGHT / 2.0);
        ripple.setBlendMode(BlendMode.SRC_OVER);

        rippleTl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(ripple.opacityProperty(), 0.0),
                        new KeyValue(ripple.radiusProperty(), 0.0)
                ),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(ripple.opacityProperty(), 0.25, Interpolator.EASE_OUT),
                        new KeyValue(ripple.radiusProperty(), HEIGHT * 0.8, Interpolator.EASE_OUT)
                ),
                new KeyFrame(Duration.millis(520),
                        new KeyValue(ripple.opacityProperty(), 0.0, Interpolator.EASE_OUT),
                        new KeyValue(ripple.radiusProperty(), HEIGHT * 1.15, Interpolator.EASE_OUT)
                )
        );
        rippleTl.play();
    }

    private void sweepGlint() {
        if (glintTl != null) glintTl.stop();

        glint.setOpacity(0.0);
        glint.setTranslateX(-getWidth() * 0.7);

        glintTl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(glint.opacityProperty(), 0.0),
                        new KeyValue(glint.translateXProperty(), -getWidth() * 0.7)
                ),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(glint.opacityProperty(), 0.55, Interpolator.EASE_OUT)
                ),
                new KeyFrame(Duration.millis(520),
                        new KeyValue(glint.translateXProperty(), getWidth() * 0.7, Interpolator.EASE_OUT)
                ),
                new KeyFrame(Duration.millis(620),
                        new KeyValue(glint.opacityProperty(), 0.0, Interpolator.EASE_OUT)
                )
        );
        glintTl.setDelay(Duration.millis(140));
        glintTl.play();
    }
}
