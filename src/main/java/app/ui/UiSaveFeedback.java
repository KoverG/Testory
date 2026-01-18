package app.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.util.Duration;

/**
 * Универсальный фидбек для кнопок: spinner -> check (animated).
 * Текст всегда по центру.
 * Индикатор (spinner/check) оверлеем слева, чтобы текст не смещался.
 */
public final class UiSaveFeedback {
    private UiSaveFeedback() {}

    public static Handle install(Button btn) {
        return install(btn, Duration.millis(1000), Duration.millis(900));
    }

    private static String toCssColor(Paint paint) {
        return paint.toString().replace("0x", "#");
    }

    public static Handle install(Button btn, Duration minSpinnerTime, Duration checkVisibleTime) {
        if (btn == null) return new Handle(null, null, null, null, null);

        int indicatorSize = 18;

        // Spinner
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(indicatorSize, indicatorSize);
        spinner.setMinSize(indicatorSize, indicatorSize);
        spinner.setMaxSize(indicatorSize, indicatorSize);
        spinner.setVisible(false);
        spinner.setManaged(false);
        spinner.getStyleClass().add("ui-savefx-spinner");

        // Check: stroke-based, чтобы можно было "рисовать"
        SVGPath check = new SVGPath();
        check.setContent("M2 10 L8 16 L18 4");
        check.setFill(null);
        check.setStrokeWidth(2.2);
        check.setStrokeLineCap(StrokeLineCap.ROUND);
        check.setStrokeLineJoin(StrokeLineJoin.ROUND);
        check.setVisible(false);
        check.setManaged(false);

        // анимация "рисования"
        check.getStrokeDashArray().setAll(24.0);
        check.setStrokeDashOffset(24.0);

        // Индикатор: в одном месте либо spinner, либо check
        StackPane indicator = new StackPane(spinner, check);
        indicator.setAlignment(Pos.CENTER);
        indicator.setMinSize(indicatorSize, indicatorSize);
        indicator.setPrefSize(indicatorSize, indicatorSize);
        indicator.setMaxSize(indicatorSize, indicatorSize);

        // Текст рисуем сами, чтобы он всегда был по центру
        Label centeredText = new Label();
        centeredText.textProperty().bind(btn.textProperty());
        centeredText.fontProperty().bind(btn.fontProperty());
        centeredText.textFillProperty().bind(btn.textFillProperty());
        centeredText.setMouseTransparent(true);

        // Корневой контейнер для кнопки: текст по центру, индикатор слева оверлеем
        StackPane root = new StackPane(centeredText, indicator);
        root.setAlignment(Pos.CENTER);

        StackPane.setAlignment(centeredText, Pos.CENTER);
        StackPane.setAlignment(indicator, Pos.CENTER_LEFT);
        StackPane.setMargin(indicator, new Insets(0, 0, 0, 12));

        // ✅ FIX: весь графический оверлей не должен перехватывать мышь,
        // иначе hover/tooltip на кнопке может "моргать" на краях.
        root.setMouseTransparent(true);
        indicator.setMouseTransparent(true);
        spinner.setMouseTransparent(true);
        check.setMouseTransparent(true);

        btn.setGraphic(root);
        btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        // Цвет галочки и spinner = цвет текста кнопки
        ChangeListener<Paint> textFillListener = (obs, ov, nv) -> {
            if (nv == null) return;

            check.setStroke(nv);
            spinner.setStyle("-fx-progress-color: " + toCssColor(nv) + ";");
        };

        btn.textFillProperty().addListener(textFillListener);

        if (btn.getTextFill() != null) {
            Paint tf = btn.getTextFill();
            check.setStroke(tf);
            spinner.setStyle("-fx-progress-color: " + toCssColor(tf) + ";");
        }

        Handle h = new Handle(btn, spinner, check, minSpinnerTime, checkVisibleTime);
        h.textFillListener = textFillListener;
        return h;
    }

    public static final class Handle {
        private final Button btn;
        private final ProgressIndicator spinner;
        private final SVGPath check;
        private final Duration minSpinnerTime;
        private final Duration checkVisibleTime;

        private ChangeListener<Paint> textFillListener;

        private long spinnerStartMs = 0L;
        private Animation running;

        private Handle(Button btn,
                       ProgressIndicator spinner,
                       SVGPath check,
                       Duration minSpinnerTime,
                       Duration checkVisibleTime) {
            this.btn = btn;
            this.spinner = spinner;
            this.check = check;
            this.minSpinnerTime = minSpinnerTime;
            this.checkVisibleTime = checkVisibleTime;
        }

        /** Показать spinner сразу. */
        public void start() {
            if (btn == null) return;
            stopRunning();

            spinnerStartMs = System.currentTimeMillis();

            spinner.setVisible(true);
            spinner.setManaged(true);

            check.setVisible(false);
            check.setManaged(false);
            check.setStrokeDashOffset(24.0);
            check.setOpacity(1.0);
            check.setScaleX(1.0);
            check.setScaleY(1.0);
        }

        /** Успешное завершение: выдержать минимум spinner time и показать check. */
        public void success() {
            if (btn == null) return;

            long elapsed = System.currentTimeMillis() - spinnerStartMs;
            long waitMs = Math.max(0L, (long) minSpinnerTime.toMillis() - elapsed);

            PauseTransition delay = new PauseTransition(Duration.millis(waitMs));
            delay.setOnFinished(e -> showCheckAnimated());
            running = delay;
            delay.playFromStart();
        }

        /** Сбросить в состояние "ничего не показываем". */
        public void reset() {
            if (btn == null) return;
            stopRunning();

            spinner.setVisible(false);
            spinner.setManaged(false);

            check.setVisible(false);
            check.setManaged(false);
            check.setStrokeDashOffset(24.0);
        }

        public void dispose() {
            reset();
            if (btn != null && textFillListener != null) {
                btn.textFillProperty().removeListener(textFillListener);
            }
        }

        private void showCheckAnimated() {
            if (!Platform.isFxApplicationThread()) {
                Platform.runLater(this::showCheckAnimated);
                return;
            }

            spinner.setVisible(false);
            spinner.setManaged(false);

            check.setVisible(true);
            check.setManaged(true);

            Timeline draw = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(check.strokeDashOffsetProperty(), 24.0, Interpolator.LINEAR),
                            new KeyValue(check.opacityProperty(), 1.0, Interpolator.LINEAR)
                    ),
                    new KeyFrame(Duration.millis(240),
                            new KeyValue(check.strokeDashOffsetProperty(), 0.0, Interpolator.EASE_OUT)
                    )
            );

            ScaleTransition pop = new ScaleTransition(Duration.millis(220), check);
            pop.setFromX(0.85);
            pop.setFromY(0.85);
            pop.setToX(1.0);
            pop.setToY(1.0);
            pop.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition appear = new ParallelTransition(draw, pop);

            PauseTransition hold = new PauseTransition(checkVisibleTime);
            hold.setOnFinished(e -> reset());

            running = new SequentialTransition(appear, hold);
            running.playFromStart();
        }

        private void stopRunning() {
            if (running != null) running.stop();
            running = null;
        }
    }
}
